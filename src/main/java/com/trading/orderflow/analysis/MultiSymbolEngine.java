package com.trading.orderflow.analysis;

import com.trading.orderflow.model.SymbolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages multiple analysis contexts.
 * Runs each context in a separate Virtual Thread.
 */
public final class MultiSymbolEngine {

    private final List<SymbolContext> contexts = new ArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void addSymbol(SymbolConfig config) {
        contexts.add(new SymbolContext(config));
    }

    public void startAll() {
        System.out.println("Starting MultiSymbolEngine for " + contexts.size() + " symbols...");
        for (SymbolContext ctx : contexts) {
            executor.submit(ctx::start);
        }
    }

    public void stopAll() {
        System.out.println("Stopping all contexts...");
        for (SymbolContext ctx : contexts) {
            ctx.stop();
        }
        executor.shutdown();
    }
}
