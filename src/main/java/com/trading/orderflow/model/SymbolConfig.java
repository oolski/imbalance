package com.trading.orderflow.model;

/**
 * Configuration for a single currency pair.
 *
 * @param symbol          Symbol name (e.g., "BTCUSDT")
 * @param imbalanceRatio  Threshold for considering an imbalance significant (e.g., 3.0)
 * @param bookDepth       Depth of the analyzed order book (e.g., 5)
 */
public record SymbolConfig(
    String symbol,
    double imbalanceRatio,
    int bookDepth
) {}
