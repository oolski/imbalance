package com.trading.orderflow;

import com.trading.orderflow.analysis.*;
import com.trading.orderflow.model.*;
import com.trading.orderflow.stream.BinanceWebSocketClient;
import com.trading.orderflow.stream.BinanceWebSocketClient.StreamType;

import java.net.URI;
import java.time.Instant;

public final class Main {

    private static final String SYMBOL          = "BTCUSDT";
    private static final double IMBALANCE_RATIO = 3.0;
    private static final int    BOOK_DEPTH      = 5;
    private static final int    PRINT_INTERVAL  = 5_000;

    public static void main(String[] args) throws Exception {
        var book    = new OrderBook();
        var tracker = new DeltaTracker();
        var history = new SnapshotHistory();
        var engine  = new SignalEngine(history);

        var bookWs = new BinanceWebSocketClient(
            new URI("wss://stream.binance.com:9443/ws/" + SYMBOL.toLowerCase() + "@depth@100ms"),
            StreamType.ORDER_BOOK,
            event -> { if (event instanceof MarketEvent.BookUpdate u) book.apply(u); },
            "OrderBook"
        );

        var tradeWs = new BinanceWebSocketClient(
            new URI("wss://stream.binance.com:9443/ws/" + SYMBOL.toLowerCase() + "@aggTrade"),
            StreamType.TRADES,
            event -> { if (event instanceof MarketEvent.TradeEvent t)
                           tracker.addTrade(t.price(), t.qty(), t.isBuyerMaker()); },
            "Trades"
        );

        bookWs.connect();
        tradeWs.connect();

        System.out.println("Nasłuchuję " + SYMBOL + "... (Ctrl+C aby zatrzymać)\n");

        Thread.ofVirtual().name("analytics").start(() -> {
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    Thread.sleep(PRINT_INTERVAL);

                    // 1. Pobierz bieżący stan
                    DeltaSnapshot delta      = tracker.snapshot();
                    var           imbalances = book.findImbalances(IMBALANCE_RATIO, BOOK_DEPTH);

                    // 2. Zapisz snapshot do historii i zresetuj deltę
                    history.add(new MarketSnapshot(
                        Instant.now(),
                        book.bestBid(), book.bestAsk(), book.spreadPct(),
                        delta.delta(), delta.cvd(),
                        delta.buyVolume(), delta.sellVolume(),
                        imbalances
                    ));
                    tracker.resetCandle();

                    // 3. Order book
                    System.out.println("""

                        ═══════════════════════════════════════
                         ORDER BOOK – Top %d poziomów
                        ═══════════════════════════════════════"""
                        .formatted(BOOK_DEPTH));
                    book.print(BOOK_DEPTH);

                    // 4. Delta
                    System.out.printf("%n── DELTA (ostatnie %ds) ──%n", PRINT_INTERVAL / 1000);
                    System.out.print(delta);

                    // 5. Imbalance
                    System.out.printf("%n── IMBALANCE (ratio > %.1fx) ──%n", IMBALANCE_RATIO);
                    if (imbalances.isEmpty()) System.out.println("  Brak istotnego imbalance");
                    else imbalances.forEach(a -> System.out.println("  " + a));

                    // 6. Historia ostatnich snapshotów
                    System.out.printf("%n── HISTORIA (bufor: %d/10) ──%n", history.size());
                    history.last(5).forEach(s ->
                        System.out.printf("  %s  mid=%.4f  delta=%+.4f  cvd=%+.4f%n",
                            s.time().toString().substring(11, 19),
                            s.midPrice(), s.delta(), s.cvd()));

                    // 7. SYGNAŁ
                    System.out.println();
                    System.out.print(engine.evaluate());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.currentThread().join();
    }
}
