package com.trading.orderflow.trading;

import com.trading.orderflow.model.SignalType;
import com.trading.orderflow.model.TradingSignal;

import java.time.Instant;

/**
 * Represents a single "paper" transaction.
 */
public record Trade(
    String symbol,
    SignalType type,
    double entryPrice,
    double stopLoss,
    double takeProfit,
    Instant openTime,
    TradeStatus status,
    double pnl,
    double pnlPct,
    Instant closeTime
) {
    public Trade(TradingSignal signal, String symbol) {
        this(
            symbol,
            signal.type(),
            signal.entryPrice(),
            signal.stopLoss(),
            signal.takeProfit(),
            signal.time(),
            TradeStatus.OPEN,
            0.0,
            0.0,
            null
        );
    }

    /**
     * Closes the transaction and calculates PnL.
     * @param closePrice Closing price.
     * @param newStatus  Closing status (TP or SL).
     * @return A new, closed Trade object.
     */
    public Trade close(double closePrice, TradeStatus newStatus) {
        double finalPnl = (type == SignalType.LONG)
            ? closePrice - entryPrice
            : entryPrice - closePrice;
        
        double finalPnlPct = (finalPnl / entryPrice) * 100.0;

        return new Trade(
            this.symbol,
            this.type,
            this.entryPrice,
            this.stopLoss,
            this.takeProfit,
            this.openTime,
            newStatus,
            finalPnl,
            finalPnlPct,
            Instant.now()
        );
    }
}
