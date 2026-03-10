package com.trading.orderflow.analysis;

import com.trading.orderflow.model.MarketSnapshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Ring buffer ostatnich N snapshotów rynku.
 * Trzyma dokładnie tyle danych, ile potrzebuje SignalEngine do analizy.
 *
 * Minimalna wymagana historia:
 *  - trend CVD:    3 snapshoty
 *  - pivot delta:  4 snapshoty (żeby wykryć odwrócenie)
 *  - wall watch:   5 snapshotów (czy ściana znika)
 *  → bezpieczny bufor: ostatnie 10 odczytów
 */
public final class SnapshotHistory {

    private static final int CAPACITY = 10;

    private final Deque<MarketSnapshot> buffer = new ArrayDeque<>(CAPACITY);

    public synchronized void add(MarketSnapshot snap) {
        if (buffer.size() >= CAPACITY) buffer.pollFirst(); // usuń najstarszy
        buffer.addLast(snap);
    }

    /** Wszystkie snapshoty od najstarszego do najnowszego */
    public synchronized List<MarketSnapshot> all() {
        return List.copyOf(buffer);
    }

    /** Ostatni snapshot lub null */
    public synchronized MarketSnapshot latest() {
        return buffer.isEmpty() ? null : buffer.peekLast();
    }

    /** Ostatnie N snapshotów (lub mniej jeśli historia krótsza) */
    public synchronized List<MarketSnapshot> last(int n) {
        var list = List.copyOf(buffer);
        int from = Math.max(0, list.size() - n);
        return list.subList(from, list.size());
    }

    public synchronized int size() { return buffer.size(); }

    // ------------------------------------------------------------------
    // Pomocnicze agregacje używane przez SignalEngine
    // ------------------------------------------------------------------

    /** Czy CVD rośnie przez ostatnie N snapshotów? */
    public synchronized boolean isCvdRising(int n) {
        var snaps = last(n);
        if (snaps.size() < 2) return false;
        for (int i = 1; i < snaps.size(); i++)
            if (snaps.get(i).cvd() <= snaps.get(i - 1).cvd()) return false;
        return true;
    }

    /** Czy CVD spada przez ostatnie N snapshotów? */
    public synchronized boolean isCvdFalling(int n) {
        var snaps = last(n);
        if (snaps.size() < 2) return false;
        for (int i = 1; i < snaps.size(); i++)
            if (snaps.get(i).cvd() >= snaps.get(i - 1).cvd()) return false;
        return true;
    }

    /** Czy delta właśnie odwróciła się na plus (była ujemna, teraz dodatnia)? */
    public synchronized boolean isDeltaFlippingBullish() {
        var snaps = last(4);
        if (snaps.size() < 2) return false;
        double prev = snaps.get(snaps.size() - 2).delta();
        double curr = snaps.get(snaps.size() - 1).delta();
        return prev < 0 && curr > 0;
    }

    /** Czy delta właśnie odwróciła się na minus? */
    public synchronized boolean isDeltaFlippingBearish() {
        var snaps = last(4);
        if (snaps.size() < 2) return false;
        double prev = snaps.get(snaps.size() - 2).delta();
        double curr = snaps.get(snaps.size() - 1).delta();
        return prev > 0 && curr < 0;
    }

    /** Średnia delty z ostatnich N snapshotów */
    public synchronized OptionalDouble avgDelta(int n) {
        return last(n).stream().mapToDouble(MarketSnapshot::delta).average();
    }

    /** Czy duża ściana SELL przy best ask zniknęła w ostatnich 2 tickach? */
    public synchronized boolean bigSellWallDisappeared(double ratio) {
        var snaps = last(3);
        if (snaps.size() < 2) return false;

        boolean hadWall = snaps.subList(0, snaps.size() - 1).stream().anyMatch(s ->
            s.imbalances().stream().anyMatch(i ->
                i.dominantSide() == com.trading.orderflow.model.Side.SELL
                && i.ratio() >= ratio
                && i.dominantPrice() >= s.bestAsk() - 0.1));

        MarketSnapshot last = snaps.getLast();
        boolean wallGone = last.imbalances().stream().noneMatch(i ->
            i.dominantSide() == com.trading.orderflow.model.Side.SELL
            && i.ratio() >= ratio);

        return hadWall && wallGone;
    }

    /** Czy duża ściana BUY przy best bid zniknęła? */
    public synchronized boolean bigBuyWallDisappeared(double ratio) {
        var snaps = last(3);
        if (snaps.size() < 2) return false;

        boolean hadWall = snaps.subList(0, snaps.size() - 1).stream().anyMatch(s ->
            s.imbalances().stream().anyMatch(i ->
                i.dominantSide() == com.trading.orderflow.model.Side.BUY
                && i.ratio() >= ratio
                && i.dominantPrice() <= s.bestBid() + 0.1));

        MarketSnapshot last = snaps.getLast();
        boolean wallGone = last.imbalances().stream().noneMatch(i ->
            i.dominantSide() == com.trading.orderflow.model.Side.BUY
            && i.ratio() >= ratio);

        return hadWall && wallGone;
    }
}
