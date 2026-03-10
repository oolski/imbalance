package com.trading.orderflow.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.orderflow.model.MarketEvent;
import com.trading.orderflow.model.PriceLevel;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic WebSocket client for Binance streams.
 * Parses messages and passes them as MarketEvent to the handler.
 */
public final class BinanceWebSocketClient extends WebSocketClient {

    private final String label;
    private final StreamType streamType;
    private final Consumer<MarketEvent> handler;
    private final ObjectMapper mapper = new ObjectMapper();

    public enum StreamType { ORDER_BOOK, TRADES }

    public BinanceWebSocketClient(URI uri, StreamType streamType,
                                   Consumer<MarketEvent> handler, String label) {
        super(uri);
        this.label      = label;
        this.streamType = streamType;
        this.handler    = handler;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("[" + label + "] Connected");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root  = mapper.readTree(message);
            MarketEvent ev = switch (streamType) {
                case ORDER_BOOK -> parseBookUpdate(root);
                case TRADES     -> parseTradeEvent(root);
            };
            handler.accept(ev);
        } catch (Exception e) {
            System.err.println("[" + label + "] Parse error: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[" + label + "] Disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[" + label + "] WS Error: " + ex.getMessage());
    }

    // -------------------------------------------------------------------------

    private MarketEvent.BookUpdate parseBookUpdate(JsonNode root) {
        return new MarketEvent.BookUpdate(
            parseLevels(root.get("b")),
            parseLevels(root.get("a"))
        );
    }

    private MarketEvent.TradeEvent parseTradeEvent(JsonNode root) {
        return new MarketEvent.TradeEvent(
            root.get("p").asDouble(),
            root.get("q").asDouble(),
            root.get("m").asBoolean()
        );
    }

    private List<PriceLevel> parseLevels(JsonNode arr) {
        var levels = new ArrayList<PriceLevel>();
        for (JsonNode e : arr)
            levels.add(new PriceLevel(e.get(0).asDouble(), e.get(1).asDouble()));
        return levels;
    }
}
