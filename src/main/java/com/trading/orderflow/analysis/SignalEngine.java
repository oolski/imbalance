package com.trading.orderflow.analysis;

import com.trading.orderflow.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes snapshot history and generates LONG / SHORT / NEUTRAL signals.
 *
 * Version 11: Stateful Engine with Feedback Loop
 * - Problem: The engine generated new signals continuously even though a position was already open.
 * - Solution: Restored state (bias) to the engine.
 *   1. After generating a LONG signal, the engine bias is locked to LONG.
 *   2. The engine remains in this bias and does not generate new signals.
 *   3. Only when TradeRegister closes the position, it calls `signalEngine.onTradeClosed()`,
 *      which resets the engine bias to NEUTRAL, allowing it to search for a new opportunity.
 * - This creates a closed loop and ensures that there is one transaction per signal.
 */
public final class SignalEngine {

    private static final int MIN_CONFIDENCE = 50;
    private static final int MACRO_TREND_WINDOW = 40;
    private static final double PRICE_VELOCITY_THRESHOLD_PCT = 0.05;
    private static final double ATR_MULTIPLIER_SL = 1.2;
    private static final double ATR_MULTIPLIER_TP = 3.6; // 1:3 RR

    private final SnapshotHistory history;
    private SignalType currentBias = SignalType.NEUTRAL; // Engine state

    public SignalEngine(SnapshotHistory history) {
        this.history = history;
    }

    /**
     * Method called by TradeRegister to inform the engine that it can search for a new opportunity.
     */
    public void onTradeClosed() {
        this.currentBias = SignalType.NEUTRAL;
        System.out.println("INFO: SignalEngine bias reset to NEUTRAL after position close.");
    }

    public TradingSignal evaluate() {
        // If the engine is in the middle of executing a signal (bias != NEUTRAL), do not look for new ones.
        if (currentBias != SignalType.NEUTRAL) {
            return neutral(List.of("Waiting for position close " + currentBias));
        }

        if (history.size() < MACRO_TREND_WINDOW) {
            return neutral(List.of("Collecting data for MACRO trend analysis (%d/%d)".formatted(history.size(), MACRO_TREND_WINDOW)));
        }

        MarketSnapshot latest = history.latest();
        var scores = calculateMicroSignals();
        double atr = estimateAtr();
        
        double cvdMacroTrend = history.getCvdTrend(MACRO_TREND_WINDOW);
        boolean isTrendUp = cvdMacroTrend > 0;
        boolean isTrendDown = cvdMacroTrend < 0;

        // --- Decision Logic ---

        if (scores.longScore() >= MIN_CONFIDENCE && isTrendUp) {
            scores.longReasons().add(0, "TREND: COMPLIANCE (Macro rising, slope: %.4f)".formatted(cvdMacroTrend));
            currentBias = SignalType.LONG; // Lock bias
            return createSignal(SignalType.LONG, latest, atr, scores.longScore(), scores.longReasons());
        }

        if (scores.shortScore() >= MIN_CONFIDENCE && isTrendDown) {
            scores.shortReasons().add(0, "TREND: COMPLIANCE (Macro falling, slope: %.4f)".formatted(cvdMacroTrend));
            currentBias = SignalType.SHORT; // Lock bias
            return createSignal(SignalType.SHORT, latest, atr, scores.shortScore(), scores.shortReasons());
        }

        return neutral(generateNeutralReasons(scores, cvdMacroTrend));
    }

    private TradingSignal createSignal(SignalType type, MarketSnapshot latest, double atr, int score, List<String> reasons) {
        double entry = type == SignalType.LONG ? latest.bestAsk() : latest.bestBid();
        double sl = type == SignalType.LONG ? entry - ATR_MULTIPLIER_SL * atr : entry + ATR_MULTIPLIER_SL * atr;
        double tp = type == SignalType.LONG ? entry + ATR_MULTIPLIER_TP * atr : entry - ATR_MULTIPLIER_TP * atr;
        return new TradingSignal(Instant.now(), type, entry, sl, tp, score, reasons);
    }

    private record Scores(int longScore, int shortScore, List<String> longReasons, List<String> shortReasons) {}

    private Scores calculateMicroSignals() {
        List<String> longReasons = new ArrayList<>();
        List<String> shortReasons = new ArrayList<>();
        int longScore = 0;
        int shortScore = 0;

        double priceChange3 = history.getPriceChangePct(3);
        if (priceChange3 > PRICE_VELOCITY_THRESHOLD_PCT) {
            longScore += 25;
            longReasons.add("PA: Bullish impulse (+%.2f%% in 15s)".formatted(priceChange3));
        }

        if (history.getCvdChange(5) > 0) { longScore += 20; longReasons.add("OF: Positive CVD change in 25s (+20)"); }
        if (history.isDeltaFlippingBullish()) { longScore += 20; longReasons.add("OF: Delta flip to positive (+20)"); }
        if (history.latest().imbalances().stream().anyMatch(i -> i.dominantSide() == Side.BUY)) { longScore += 15; longReasons.add("OF: Imbalance present on BUY side (+15)"); }

        if (priceChange3 < -PRICE_VELOCITY_THRESHOLD_PCT) {
            shortScore += 25;
            shortReasons.add("PA: Bearish impulse (%.2f%% in 15s)".formatted(priceChange3));
        }

        if (history.getCvdChange(5) < 0) { shortScore += 20; shortReasons.add("OF: Negative CVD change in 25s (+20)"); }
        if (history.isDeltaFlippingBearish()) { shortScore += 20; shortReasons.add("OF: Delta flip to negative (+20)"); }
        if (history.latest().imbalances().stream().anyMatch(i -> i.dominantSide() == Side.SELL)) { shortScore += 15; shortReasons.add("OF: Imbalance present on SELL side (+15)"); }

        return new Scores(longScore, shortScore, longReasons, shortReasons);
    }

    private List<String> generateNeutralReasons(Scores scores, double cvdMacroTrend) {
        List<String> reasons = new ArrayList<>();
        String trendDirection = cvdMacroTrend > 0 ? "Bullish" : (cvdMacroTrend < 0 ? "Bearish" : "Flat");
        reasons.add("Macro Trend (3.3m): %s (slope: %.4f)".formatted(trendDirection, cvdMacroTrend));
        reasons.add("Micro Signal: Long Score %d, Short Score %d (threshold: %d)".formatted(scores.longScore(), scores.shortScore(), MIN_CONFIDENCE));
        
        return reasons;
    }
    
    private TradingSignal neutral(List<String> reasons) {
        MarketSnapshot latest = history.latest();
        double mid = latest != null ? latest.midPrice() : 0;
        return new TradingSignal(Instant.now(), SignalType.NEUTRAL, mid, 0, 0, 0, reasons);
    }

    private double estimateAtr() {
        var snaps = history.all();
        if (snaps.size() < 2) return 1.0;
        double max = snaps.stream().mapToDouble(MarketSnapshot::midPrice).max().orElse(0);
        double min = snaps.stream().mapToDouble(MarketSnapshot::midPrice).min().orElse(0);
        double range = max - min;
        return range < 0.5 ? 1.0 : range;
    }
}
