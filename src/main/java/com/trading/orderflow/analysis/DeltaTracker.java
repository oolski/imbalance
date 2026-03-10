package com.trading.orderflow.analysis;

import com.trading.orderflow.model.DeltaSnapshot;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks buy/sell volume from the trade stream.
 * Thread-safe without locks - uses AtomicReference on an immutable State record.
 */
public final class DeltaTracker {

    private record State(double buy, double sell, double cvd, double lastPrice) {
        State addTrade(double price, double qty, boolean buyerMaker) {
            return buyerMaker
                ? new State(buy, sell + qty, cvd - qty, price)
                : new State(buy + qty, sell, cvd + qty, price);
        }
        State resetCandle() { return new State(0, 0, cvd, lastPrice); }
    }

    private final AtomicReference<State> state =
        new AtomicReference<>(new State(0, 0, 0, 0));

    public void addTrade(double price, double qty, boolean buyerMaker) {
        state.updateAndGet(s -> s.addTrade(price, qty, buyerMaker));
    }

    public DeltaSnapshot snapshot() {
        State s = state.get();
        return new DeltaSnapshot(s.buy(), s.sell(), s.buy() - s.sell(), s.cvd(), s.lastPrice());
    }

    public void resetCandle() {
        state.updateAndGet(State::resetCandle);
    }
}
