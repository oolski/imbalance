package com.trading.orderflow.analysis;

import com.trading.orderflow.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Analizuje historię snapshotów i generuje sygnały LONG / SHORT / NEUTRAL.
 *
 * Punktacja (confidence 0–100):
 *
 * LONG punkty:
 *   +30  CVD rośnie przez ostatnie 3 snapshoty
 *   +20  Delta właśnie odwróciła się na plus (flip)
 *   +15  Średnia delty z 3 ostatnich > 0
 *   +20  Duża ściana SELL zniknęła (absorbed)
 *   +15  BUY imbalance przy bestBid (popyt broniony)
 *
 * SHORT punkty:
 *   +30  CVD spada przez ostatnie 3 snapshoty
 *   +20  Delta właśnie odwróciła się na minus (flip)
 *   +15  Średnia delty z 3 ostatnich < 0
 *   +20  Duża ściana BUY zniknęła
 *   +15  SELL imbalance przy bestAsk (podaż broniona)
 *
 * Sygnał emitowany gdy confidence >= MIN_CONFIDENCE.
 */
public final class SignalEngine {

    private static final int    MIN_CONFIDENCE   = 50;
    private static final double BIG_WALL_RATIO   = 10.0;  // "duża" ściana to ratio >= 10x
    private static final double ATR_MULTIPLIER_SL = 1.2;
    private static final double ATR_MULTIPLIER_TP = 2.0;

    private final SnapshotHistory history;

    public SignalEngine(SnapshotHistory history) {
        this.history = history;
    }

    public TradingSignal evaluate() {
        if (history.size() < 3) {
            return neutral(List.of("Za mało danych (potrzeba min. 3 snapshoty)"));
        }

        MarketSnapshot latest = history.latest();
        double mid = latest.midPrice();

        // --- Oblicz przybliżone ATR z historii (zakres cen) ---
        double atr = estimateAtr();

        // --- Scoring LONG ---
        int longScore = 0;
        List<String> longReasons = new ArrayList<>();

        if (history.isCvdRising(3)) {
            longScore += 30;
            longReasons.add("CVD rośnie 3 okresy z rzędu (+30)");
        }
        if (history.isDeltaFlippingBullish()) {
            longScore += 20;
            longReasons.add("Delta odwróciła się z ujemnej na dodatnią (+20)");
        }
        double avgDelta = history.avgDelta(3).orElse(0);
        if (avgDelta > 0) {
            longScore += 15;
            longReasons.add("Średnia delty > 0: %.4f (+15)".formatted(avgDelta));
        }
        if (history.bigSellWallDisappeared(BIG_WALL_RATIO)) {
            longScore += 20;
            longReasons.add("Duża ściana SELL (%.0fx) została wchłonięta (+20)".formatted(BIG_WALL_RATIO));
        }
        boolean hasBuyImbalanceNearBid = latest.imbalances().stream().anyMatch(i ->
            i.dominantSide() == Side.BUY && i.dominantPrice() >= latest.bestBid() - 0.5);
        if (hasBuyImbalanceNearBid) {
            longScore += 15;
            longReasons.add("BUY imbalance przy best bid – popyt aktywnie broniony (+15)");
        }

        // --- Scoring SHORT ---
        int shortScore = 0;
        List<String> shortReasons = new ArrayList<>();

        if (history.isCvdFalling(3)) {
            shortScore += 30;
            shortReasons.add("CVD spada 3 okresy z rzędu (+30)");
        }
        if (history.isDeltaFlippingBearish()) {
            shortScore += 20;
            shortReasons.add("Delta odwróciła się z dodatniej na ujemną (+20)");
        }
        if (avgDelta < 0) {
            shortScore += 15;
            shortReasons.add("Średnia delty < 0: %.4f (+15)".formatted(avgDelta));
        }
        if (history.bigBuyWallDisappeared(BIG_WALL_RATIO)) {
            shortScore += 20;
            shortReasons.add("Duża ściana BUY (%.0fx) została wchłonięta (+20)".formatted(BIG_WALL_RATIO));
        }
        boolean hasSellImbalanceNearAsk = latest.imbalances().stream().anyMatch(i ->
            i.dominantSide() == Side.SELL && i.dominantPrice() <= latest.bestAsk() + 0.5);
        if (hasSellImbalanceNearAsk) {
            shortScore += 15;
            shortReasons.add("SELL imbalance przy best ask – podaż aktywnie broniona (+15)");
        }

        // --- Decyzja ---
        if (longScore >= MIN_CONFIDENCE && longScore > shortScore) {
            return new TradingSignal(
                Instant.now(),
                SignalType.LONG,
                latest.bestAsk(),                         // wejście market buy
                latest.bestAsk() - ATR_MULTIPLIER_SL * atr,
                latest.bestAsk() + ATR_MULTIPLIER_TP * atr,
                longScore,
                longReasons
            );
        }

        if (shortScore >= MIN_CONFIDENCE && shortScore > longScore) {
            return new TradingSignal(
                Instant.now(),
                SignalType.SHORT,
                latest.bestBid(),                         // wejście market sell
                latest.bestBid() + ATR_MULTIPLIER_SL * atr,
                latest.bestBid() - ATR_MULTIPLIER_TP * atr,
                shortScore,
                shortReasons
            );
        }

        // Zbierz wszystkie powody braku sygnału
        List<String> neutralReasons = new ArrayList<>();
        neutralReasons.add("Long score: %d / Short score: %d (min: %d)".formatted(longScore, shortScore, MIN_CONFIDENCE));
        if (longScore > 0)  neutralReasons.addAll(longReasons.stream().map(r -> "[L] " + r).toList());
        if (shortScore > 0) neutralReasons.addAll(shortReasons.stream().map(r -> "[S] " + r).toList());
        return neutral(neutralReasons);
    }

    // ------------------------------------------------------------------

    private TradingSignal neutral(List<String> reasons) {
        MarketSnapshot latest = history.latest();
        double mid = latest != null ? latest.midPrice() : 0;
        return new TradingSignal(Instant.now(), SignalType.NEUTRAL, mid, 0, 0, 0, reasons);
    }

    /** Przybliżone ATR jako zakres mid cen z historii */
    private double estimateAtr() {
        var snaps = history.all();
        if (snaps.size() < 2) return 1.0;
        double max = snaps.stream().mapToDouble(MarketSnapshot::midPrice).max().orElse(0);
        double min = snaps.stream().mapToDouble(MarketSnapshot::midPrice).min().orElse(0);
        double range = max - min;
        return range < 0.5 ? 1.0 : range; // fallback żeby SL/TP miały sens
    }
}
