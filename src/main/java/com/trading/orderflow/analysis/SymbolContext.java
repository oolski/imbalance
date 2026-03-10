package com.trading.orderflow.analysis;

import com.trading.orderflow.model.*;
import com.trading.orderflow.stream.BinanceWebSocketClient;
import com.trading.orderflow.stream.BinanceWebSocketClient.StreamType;
import com.trading.orderflow.trading.TradeRegister;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated analysis context for a single currency pair.
 * Runs its own Virtual Thread for analysis and manages its own WebSockets.
 */
public final class SymbolContext {

    private final SymbolConfig config;
    private final OrderBook book;
    private final DeltaTracker tracker;
    private final SnapshotHistory history;
    private final SignalEngine engine;
    private final TradeRegister tradeRegister;
    
    private final BinanceWebSocketClient bookWs;
    private final BinanceWebSocketClient tradeWs;
    
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SymbolContext(SymbolConfig config) {
        this.config = config;
        this.book = new OrderBook();
        this.tracker = new DeltaTracker();
        this.history = new SnapshotHistory();
        this.engine = new SignalEngine(history);
        this.tradeRegister = new TradeRegister(config.symbol());

        try {
            this.bookWs = new BinanceWebSocketClient(
                new URI("wss://stream.binance.com:9443/ws/" + config.symbol().toLowerCase() + "@depth@100ms"),
                StreamType.ORDER_BOOK,
                event -> { if (event instanceof MarketEvent.BookUpdate u) book.apply(u); },
                config.symbol() + "-Book"
            );

            this.tradeWs = new BinanceWebSocketClient(
                new URI("wss://stream.binance.com:9443/ws/" + config.symbol().toLowerCase() + "@aggTrade"),
                StreamType.TRADES,
                event -> { if (event instanceof MarketEvent.TradeEvent t)
                               tracker.addTrade(t.price(), t.qty(), t.isBuyerMaker()); },
                config.symbol() + "-Trade"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize websockets for " + config.symbol(), e);
        }
    }

    public void start() {
        if (running.getAndSet(true)) return;

        bookWs.connect();
        tradeWs.connect();

        Thread.ofVirtual().name("ctx-" + config.symbol()).start(this::analysisLoop);
    }

    public void stop() {
        running.set(false);
        bookWs.close();
        tradeWs.close();
        System.out.println("[" + config.symbol() + "] SUMMARY: " + tradeRegister.getSummary());
    }

    private void analysisLoop() {
        System.out.println("[" + config.symbol() + "] Analysis started");
        try {
            while (running.get()) {
                Thread.sleep(5000); // Analysis interval

                // 1. Snapshot
                DeltaSnapshot delta = tracker.snapshot();
                var imbalances = book.findImbalances(config.imbalanceRatio(), config.bookDepth());

                // 2. Update history
                MarketSnapshot snap = new MarketSnapshot(
                    Instant.now(),
                    book.bestBid(), book.bestAsk(), book.spreadPct(),
                    delta.delta(), delta.cvd(),
                    delta.buyVolume(), delta.sellVolume(),
                    imbalances
                );
                history.add(snap);
                tracker.resetCandle();

                // 3. Update Paper Trading & Feedback Loop
                boolean tradeClosed = tradeRegister.updateTrades(snap);
                if (tradeClosed) {
                    engine.onTradeClosed();
                }

                // 4. Evaluate Signal
                TradingSignal signal = engine.evaluate();

                // 5. Register Trade if Signal
                if (signal.type() != SignalType.NEUTRAL) {
                    tradeRegister.addTrade(signal);
                    printSignal(signal, snap);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void printSignal(TradingSignal signal, MarketSnapshot snap) {
        synchronized (System.out) {
            System.out.println("\n" + "=".repeat(50));
            System.out.printf(" SYMBOL: %s  |  PRICE: %.2f%n", config.symbol(), snap.midPrice());
            System.out.println("=".repeat(50));
            System.out.print(signal);
            System.out.println("=".repeat(50) + "\n");
        }
    }
}
