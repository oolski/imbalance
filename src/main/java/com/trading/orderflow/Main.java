package com.trading.orderflow;

import com.trading.orderflow.analysis.MultiSymbolEngine;
import com.trading.orderflow.model.SymbolConfig;

import java.util.List;

public final class Main {

    public static void main(String[] args) throws InterruptedException {
        List<SymbolConfig> configs = List.of(
            new SymbolConfig("BTCUSDT", 3.0, 5),
            new SymbolConfig("ETHUSDT", 3.0, 5),
            new SymbolConfig("BNBUSDT", 2.5, 5)
        );

        MultiSymbolEngine engine = new MultiSymbolEngine();
        configs.forEach(engine::addSymbol);

        engine.startAll();

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(engine::stopAll));

        // Keep main thread alive
        Thread.currentThread().join();
    }
}
