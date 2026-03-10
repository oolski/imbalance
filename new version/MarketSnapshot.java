package com.trading.orderflow.model;

import java.time.Instant;
import java.util.List;

/**
 * Pełny stan rynku w jednej chwili – zapisywany do historii.
 */
public record MarketSnapshot(
    Instant time,
    double  bestBid,
    double  bestAsk,
    double  spreadPct,
    double  delta,          // delta bieżącej świecy
    double  cvd,            // cumulative volume delta od startu
    double  buyVolume,
    double  sellVolume,
    List<ImbalanceAlert> imbalances
) {
    /** Środek spreadu */
    public double midPrice() { return (bestBid + bestAsk) / 2.0; }

    /** Presja kupna: jaka część wolumenu to buy */
    public double buyRatio() {
        double total = buyVolume + sellVolume;
        return total == 0 ? 0.5 : buyVolume / total;
    }
}
