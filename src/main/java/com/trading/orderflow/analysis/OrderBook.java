package com.trading.orderflow.analysis;

import com.trading.orderflow.model.ImbalanceAlert;
import com.trading.orderflow.model.MarketEvent;
import com.trading.orderflow.model.PriceLevel;
import com.trading.orderflow.model.Side;

import java.util.*;

/**
 * Holds the current state of the order book and detects imbalances.
 */
public final class OrderBook {

    private final TreeMap<Double, Double> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Double, Double> asks = new TreeMap<>();

    public synchronized void apply(MarketEvent.BookUpdate update) {
        update.bids().forEach(l -> updateSide(bids, l));
        update.asks().forEach(l -> updateSide(asks, l));
    }

    private void updateSide(TreeMap<Double, Double> side, PriceLevel level) {
        if (level.qty() == 0) side.remove(level.price());
        else side.put(level.price(), level.qty());
    }

    public synchronized double bestBid() { return bids.isEmpty() ? 0 : bids.firstKey(); }
    public synchronized double bestAsk() { return asks.isEmpty() ? 0 : asks.firstKey(); }

    public synchronized double spreadPct() {
        double b = bestBid(), a = bestAsk();
        return (b == 0 || a == 0) ? 0 : (a - b) / b * 100;
    }

    public synchronized List<ImbalanceAlert> findImbalances(double ratio, int depth) {
        var bidList = new ArrayList<>(bids.entrySet());
        var askList = new ArrayList<>(asks.entrySet());
        int limit   = Math.min(depth, Math.min(bidList.size(), askList.size()));

        List<ImbalanceAlert> result = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            double bidQty = bidList.get(i).getValue();
            double askQty = askList.get(i).getValue();
            double bidPx  = bidList.get(i).getKey();
            double askPx  = askList.get(i).getKey();

            if (askQty > 0 && bidQty / askQty >= ratio)
                result.add(new ImbalanceAlert(Side.BUY,  bidPx, bidQty, askQty, bidQty / askQty));
            if (bidQty > 0 && askQty / bidQty >= ratio)
                result.add(new ImbalanceAlert(Side.SELL, askPx, askQty, bidQty, askQty / bidQty));
        }
        return result;
    }

    public synchronized void print(int depth) {
        var askList = new ArrayList<>(asks.entrySet());
        for (int i = Math.min(depth, askList.size()) - 1; i >= 0; i--)
            System.out.printf("  ASK  %.4f  |  %.6f%n",
                askList.get(i).getKey(), askList.get(i).getValue());

        System.out.printf("  --- SPREAD %.5f%% ---%n", spreadPct());

        int count = 0;
        for (var e : bids.entrySet()) {
            System.out.printf("  BID  %.4f  |  %.6f%n", e.getKey(), e.getValue());
            if (++count >= depth) break;
        }
    }
}
