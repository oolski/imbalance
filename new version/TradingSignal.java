package com.trading.orderflow.model;

import java.time.Instant;
import java.util.List;

/**
 * Sygnał tradingowy z uzasadnieniem i poziomami SL/TP.
 */
public record TradingSignal(
    Instant    time,
    SignalType type,
    double     entryPrice,
    double     stopLoss,
    double     takeProfit,
    int        confidence,   // 0–100
    List<String> reasons
) {
    @Override
    public String toString() {
        String arrow = switch (type) {
            case LONG    -> "🟢 TERAZ LONG";
            case SHORT   -> "🔴 TERAZ SHORT";
            case NEUTRAL -> "⚪ BRAK SYGNAŁU";
        };
        String levels = type == SignalType.NEUTRAL ? "" :
            "  Entry: %.4f  |  SL: %.4f  |  TP: %.4f%n".formatted(entryPrice, stopLoss, takeProfit);

        String reasonStr = reasons.stream()
            .map(r -> "    • " + r)
            .reduce("", (a, b) -> a + b + "\n");

        return """
            ╔══════════════════════════════════════╗
              %s  (pewność: %d/100)
            %s  Powody:
            %s╚══════════════════════════════════════╝
            """.formatted(arrow, confidence, levels, reasonStr);
    }
}
