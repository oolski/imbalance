package com.trading.orderflow.model;

public record DeltaSnapshot(double buyVolume, double sellVolume, double delta, double cvd, double lastPrice) {
    @Override
    public String toString() {
        return String.format("  BUY: %.4f | SELL: %.4f | DELTA: %.4f | CVD: %.4f | LAST: %.2f",
            buyVolume, sellVolume, delta, cvd, lastPrice);
    }
}
