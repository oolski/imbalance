package com.trading.orderflow.trading;

public enum TradeStatus {
    OPEN,
    CLOSED_TP, // Closed by Take Profit
    CLOSED_SL  // Closed by Stop Loss
}
