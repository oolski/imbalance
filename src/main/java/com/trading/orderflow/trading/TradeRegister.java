package com.trading.orderflow.trading;

import com.trading.orderflow.model.MarketSnapshot;
import com.trading.orderflow.model.SignalType;
import com.trading.orderflow.model.TradingSignal;

import java.util.ArrayList;
import java.util.List;

/**
 * Trade Register (Paper Trading).
 * Manages a list of open and closed positions.
 */
public class TradeRegister {

    private final List<Trade> openTrades = new ArrayList<>();
    private final List<Trade> closedTrades = new ArrayList<>();
    private final String symbol;

    public TradeRegister(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Adds a new trade if there isn't already an open one for this symbol.
     */
    public synchronized void addTrade(TradingSignal signal) {
        if (signal.type() == SignalType.NEUTRAL) return;

        // Allow only one open position per symbol
        if (openTrades.isEmpty()) {
            Trade newTrade = new Trade(signal, symbol);
            openTrades.add(newTrade);
            System.out.printf("[%s] OPEN POSITION: %s @ %.2f (SL: %.2f, TP: %.2f)%n",
                symbol, newTrade.type(), newTrade.entryPrice(), newTrade.stopLoss(), newTrade.takeProfit());
        }
    }

    /**
     * Updates open positions based on the new market price.
     * Closes positions that have reached TP or SL.
     * @return true if any position was closed, false otherwise.
     */
    public synchronized boolean updateTrades(MarketSnapshot snapshot) {
        List<Trade> tradesToClose = new ArrayList<>();
        List<Trade> stillOpen = new ArrayList<>();
        boolean anyClosed = false;

        for (Trade trade : openTrades) {
            boolean closed = false;
            if (trade.type() == SignalType.LONG) {
                if (snapshot.bestAsk() >= trade.takeProfit()) {
                    tradesToClose.add(trade.close(trade.takeProfit(), TradeStatus.CLOSED_TP));
                    closed = true;
                } else if (snapshot.bestAsk() <= trade.stopLoss()) {
                    tradesToClose.add(trade.close(trade.stopLoss(), TradeStatus.CLOSED_SL));
                    closed = true;
                }
            } else { // SHORT
                if (snapshot.bestBid() <= trade.takeProfit()) {
                    tradesToClose.add(trade.close(trade.takeProfit(), TradeStatus.CLOSED_TP));
                    closed = true;
                } else if (snapshot.bestBid() >= trade.stopLoss()) {
                    tradesToClose.add(trade.close(trade.stopLoss(), TradeStatus.CLOSED_SL));
                    closed = true;
                }
            }

            if (!closed) {
                stillOpen.add(trade);
            }
        }

        if (!tradesToClose.isEmpty()) {
            closedTrades.addAll(tradesToClose);
            openTrades.clear();
            openTrades.addAll(stillOpen);
            anyClosed = true;

            tradesToClose.forEach(t -> 
                System.out.printf("[%s] CLOSED POSITION: %s | PnL: %.2f (%.2f%%)%n",
                    symbol, t.status(), t.pnl(), t.pnlPct())
            );
        }
        
        return anyClosed;
    }

    public synchronized List<Trade> getClosedTrades() {
        return List.copyOf(closedTrades);
    }

    public synchronized String getSummary() {
        long wins = closedTrades.stream().filter(t -> t.status() == TradeStatus.CLOSED_TP).count();
        long losses = closedTrades.stream().filter(t -> t.status() == TradeStatus.CLOSED_SL).count();
        double totalPnl = closedTrades.stream().mapToDouble(Trade::pnl).sum();
        double winRate = (wins + losses) > 0 ? (double) wins / (wins + losses) * 100.0 : 0;

        return String.format("Trades: %d | Wins: %d | Losses: %d | Win Rate: %.1f%% | Total PnL: %.2f",
            closedTrades.size(), wins, losses, winRate, totalPnl);
    }
}
