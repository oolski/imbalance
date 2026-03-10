package com.trading.orderflow.analysis;

import com.trading.orderflow.model.MarketSnapshot;
import com.trading.orderflow.model.Side;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Ring buffer of the last N market snapshots.
 * Version 4: Added methods for analyzing price change (Price Action) and CVD change.
 */
public final class SnapshotHistory {

    private static final int CAPACITY = 100;

    private final Deque<MarketSnapshot> buffer = new ArrayDeque<>(CAPACITY);

    public synchronized void add(MarketSnapshot snap) {
        if (buffer.size() >= CAPACITY) buffer.pollFirst();
        buffer.addLast(snap);
    }

    public synchronized List<MarketSnapshot> all() {
        return List.copyOf(buffer);
    }

    public synchronized MarketSnapshot latest() {
        return buffer.isEmpty() ? null : buffer.peekLast();
    }

    public synchronized List<MarketSnapshot> last(int n) {
        var list = List.copyOf(buffer);
        int from = Math.max(0, list.size() - n);
        return list.subList(from, list.size());
    }

    public synchronized int size() { return buffer.size(); }

    // --- Analytic methods ---

    /** Percentage price change (mid) over the last N periods */
    public synchronized double getPriceChangePct(int n) {
        var snaps = last(n + 1); // Need n+1 points to have n intervals
        if (snaps.size() < 2) return 0.0;
        double current = snaps.getLast().midPrice();
        double old = snaps.getFirst().midPrice();
        return (current - old) / old * 100.0;
    }

    /** Net CVD change over the last N periods */
    public synchronized double getCvdChange(int n) {
        var snaps = last(n + 1);
        if (snaps.size() < 2) return 0.0;
        return snaps.getLast().cvd() - snaps.getFirst().cvd();
    }

    public synchronized boolean isDeltaFlippingBullish() {
        var snaps = last(2);
        if (snaps.size() < 2) return false;
        return snaps.get(0).delta() < 0 && snaps.get(1).delta() > 0;
    }

    public synchronized boolean isDeltaFlippingBearish() {
        var snaps = last(2);
        if (snaps.size() < 2) return false;
        return snaps.get(0).delta() > 0 && snaps.get(1).delta() < 0;
    }

    public synchronized OptionalDouble avgDelta(int n) {
        return last(n).stream().mapToDouble(MarketSnapshot::delta).average();
    }

    public synchronized double avgAbsDelta(int n) {
        return last(n).stream()
            .mapToDouble(s -> Math.abs(s.delta()))
            .average()
            .orElse(0.0);
    }

    public synchronized double getCvdTrend(int n) {
        var snaps = last(n);
        if (snaps.size() < 2) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < snaps.size(); i++) {
            double x = i;
            double y = snaps.get(i).cvd();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        int count = snaps.size();
        double denominator = count * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-9) return 0.0;

        return (count * sumXY - sumX * sumY) / denominator;
    }

    public synchronized boolean bigSellWallDisappeared(double ratio) {
        var snaps = last(2);
        if (snaps.size() < 2) return false;
        var prev = snaps.get(0);
        var curr = snaps.get(1);

        boolean hadWall = prev.imbalances().stream().anyMatch(i ->
            i.dominantSide() == Side.SELL && i.ratio() >= ratio);
        boolean wallGone = curr.imbalances().stream().noneMatch(i ->
            i.dominantSide() == Side.SELL && i.ratio() >= ratio);

        return hadWall && wallGone;
    }

    public synchronized boolean bigBuyWallDisappeared(double ratio) {
        var snaps = last(2);
        if (snaps.size() < 2) return false;
        var prev = snaps.get(0);
        var curr = snaps.get(1);

        boolean hadWall = prev.imbalances().stream().anyMatch(i ->
            i.dominantSide() == Side.BUY && i.ratio() >= ratio);
        boolean wallGone = curr.imbalances().stream().noneMatch(i ->
            i.dominantSide() == Side.BUY && i.ratio() >= ratio);

        return hadWall && wallGone;
    }
}
