# Order Flow Analysis Engine

A high-performance, concurrent trading engine for cryptocurrency markets (Binance). It analyzes real-time Order Book and Trade data to generate trading signals based on Order Flow, Delta, and Price Action.

## Key Features

*   **Multi-Symbol Support:** Monitors multiple pairs (e.g., BTCUSDT, ETHUSDT) simultaneously using Virtual Threads for high concurrency.
*   **Real-time Order Flow Analysis:**
    *   **Imbalance Detection:** Identifies significant supply/demand imbalances in the Order Book.
    *   **Delta & CVD:** Tracks buying/selling pressure and Cumulative Volume Delta trends.
    *   **Price Action:** Analyzes price velocity and momentum.
*   **Signal Engine (v11):**
    *   **Dual-Timeframe Filter:** Combines a Macro Trend (3.3 min) filter with Micro Entry signals.
    *   **Trend Alignment:** strictly forbids trading against the dominant momentum.
    *   **Scoring System:** Evaluates trades based on a composite score of Delta, CVD, Imbalance, and Price Action.
    *   **Stateful Logic:** Prevents over-trading by locking the engine state until the current position is closed.
*   **Paper Trading Module:**
    *   Simulates trade execution with a fixed 1:3 Risk:Reward ratio.
    *   Tracks PnL and Win Rate in real-time.
    *   Provides a feedback loop to the Signal Engine to enforce a "one trade at a time" policy.

## Architecture

The project is built with **Java 21** and structured into the following components:

*   **`com.trading.orderflow`**: Main entry point.
*   **`com.trading.orderflow.analysis`**:
    *   `SymbolContext`: Orchestrates the pipeline for a single symbol (WebSockets -> Analysis -> Trading).
    *   `SignalEngine`: The brain. Decides when to enter LONG/SHORT based on `SnapshotHistory`.
    *   `SnapshotHistory`: A ring buffer storing market states for trend analysis.
    *   `DeltaTracker` & `OrderBook`: Maintain the real-time state of the market.
*   **`com.trading.orderflow.trading`**:
    *   `TradeRegister`: Manages open/closed positions and calculates PnL.
*   **`com.trading.orderflow.stream`**:
    *   `BinanceWebSocketClient`: Generic client for `depth` and `aggTrade` streams.

## Requirements

*   Java 21 (or higher)
*   Maven

## How to Run

1.  **Build the project:**
    ```bash
    mvn clean package
    ```

2.  **Run the application:**
    ```bash
    java -jar target/orderflow-1.0-SNAPSHOT.jar
    ```
    *Note: Ensure your Java runtime matches the version used for building.*

## Configuration

You can configure the symbols and parameters in `Main.java`:

```java
List<SymbolConfig> configs = List.of(
    new SymbolConfig("BTCUSDT", 3.0, 5), // Symbol, Imbalance Ratio, Book Depth
    new SymbolConfig("ETHUSDT", 3.0, 5)
);
```

## Strategy Logic

The engine uses a scoring system (0-100) to evaluate signals. A trade is taken if:
1.  **Macro Trend** (Slope of CVD over 3.3m) aligns with the signal direction.
2.  **Micro Score** (based on recent Delta flips, CVD changes, and Imbalances) exceeds the threshold (50).

**Risk Management:**
*   **Stop Loss:** 1.2x ATR (Average True Range estimate).
*   **Take Profit:** 3.6x ATR (Risk:Reward 1:3).

## Disclaimer

This software is for educational and research purposes only. It is not financial advice. Use at your own risk.
