package com.trading.orderflow.model;

import java.time.Instant;
import java.util.List;

/**
 * A trading signal with justification and SL/TP levels.
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
        String signalLabel = switch (type) {
            case LONG    -> "NOW LONG";
            case SHORT   -> "NOW SHORT";
            case NEUTRAL -> "NO SIGNAL";
        };
        String levels = type == SignalType.NEUTRAL ? "" :
            "  Entry: %.4f  |  SL: %.4f  |  TP: %.4f%n".formatted(entryPrice, stopLoss, takeProfit);

        String reasonStr = reasons.stream()
            .map(r -> "    • " + r)
            .reduce("", (a, b) -> a + b + "\n");

        return """
            ╔══════════════════════════════════════╗
              %s  (confidence: %d/100)
            %s  Reasons:
            %s╚══════════════════════════════════════╝
            """.formatted(signalLabel, confidence, levels, reasonStr);
    }
}
