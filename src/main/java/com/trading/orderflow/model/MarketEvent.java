package com.trading.orderflow.model;

import java.util.List;

/**
 * Sealed interface modeling WebSocket events.
 * The compiler knows all variants, so switch is exhaustive without default.
 */
public sealed interface MarketEvent
    permits MarketEvent.BookUpdate, MarketEvent.TradeEvent {

    record BookUpdate(List<PriceLevel> bids, List<PriceLevel> asks)
        implements MarketEvent {}

    record TradeEvent(double price, double qty, boolean isBuyerMaker)
        implements MarketEvent {}
}
