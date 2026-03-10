package com.trading.orderflow.model;

import java.time.Instant;
import java.util.List;

/**
 * Full market state at a given moment - saved to history.
 */
public record MarketSnapshot(
    Instant time,
    double  bestBid,
    double  bestAsk,
    double  spreadPct,
    double  delta,          // delta of the current candle
    double  cvd,            // cumulative volume delta since start
    double  buyVolume,
    double  sellVolume,
    List<ImbalanceAlert> imbalances
) {
    /** Mid price of the spread */
    public double midPrice() { return (bestBid + bestAsk) / 2.0; }

    /** Buying pressure: what part of the volume is buy */
    public double buyRatio() {
        double total = buyVolume + sellVolume;
        return total == 0 ? 0.5 : buyVolume / total;
    }
}
