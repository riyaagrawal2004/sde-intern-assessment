package com.indothai.position;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * HTTP layer for the Position Maintaining Service.
 *
 * Endpoints:
 *  - POST /events    : receive one order event (JSON body), apply it to the PositionStore.
 *  - GET  /position   : return current net position for every symbol seen so far.
 *
 * Communication choice: plain HTTP + JSON.
 *  - Why: the assessment explicitly allows it ("HTTP between the two services
 *    is an acceptable solution"), it needs no extra infrastructure (no broker
 *    to install/run), and it's trivial to test with curl or an HTTP client --
 *    which matches "simple, correct solution preferred over unnecessary infra".
 *  - Delivery limitations: this is at-most-once / best-effort over a single
 *    HTTP request per event. If the Position Service is down or a request
 *    times out, the Order Update Service logs a send failure and moves on --
 *    there is no retry queue or durable delivery (explicitly out of scope
 *    per the assessment PDF).
 */
public class PositionHttpServer {

    private static final Logger LOGGER = Logger.getLogger(PositionHttpServer.class.getName());

    private final PositionStore store;
    private final HttpServer server;

    public PositionHttpServer(PositionStore store, String host, int port) throws IOException {
        this.store = store;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/events", new EventsHandler());
        this.server.createContext("/position", new PositionHandler());
        // Fixed thread pool so /position stays responsive while /events is
        // being hit repeatedly -- satisfies "keep endpoint available while
        // events are being processed".
        this.server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() {
        server.start();
        LOGGER.info(() -> "Position Maintaining Service listening on "
                + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }
    
    public java.net.InetSocketAddress getAddress() {
        return server.getAddress();
    }

    private class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, SimpleJson.errorJson("Method not allowed, use POST"));
                return;
            }

            String body;
            try (var is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            Map<String, String> fields;
            try {
                fields = SimpleJson.parseFlatObject(body);
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Rejected malformed JSON on /events: " + e.getMessage());
                sendJson(exchange, 400, SimpleJson.errorJson("Malformed JSON body"));
                return;
            }

            String eventId = fields.get("event_id");
            String symbol = fields.get("symbol");
            String transactionType = fields.get("transaction_type");
            String quantityRaw = fields.get("quantity");

            // Defensive re-validation at the HTTP boundary. The Order Update
            // Service already validates rows against the Event Contract, but
            // this service must not crash or misbehave on bad input from any
            // caller (PDF: "Malformed input must not crash either service").
            String validationError = validate(eventId, symbol, transactionType, quantityRaw);
            if (validationError != null) {
                LOGGER.warning("Rejected event on /events: " + validationError + " payload=" + body);
                sendJson(exchange, 400, SimpleJson.errorJson(validationError));
                return;
            }

            int quantity = Integer.parseInt(quantityRaw);
            PositionStore.ApplyResult result = store.applyEvent(eventId, symbol, transactionType, quantity);

            if (result == PositionStore.ApplyResult.DUPLICATE) {
                LOGGER.info(() -> "Duplicate event_id ignored: " + eventId);
                sendJson(exchange, 200, "{\"status\":\"duplicate\",\"event_id\":\"" + escape(eventId) + "\"}");
            } else {
                LOGGER.info(() -> "Applied event " + eventId + " (" + transactionType + " " + quantity + " " + symbol + ")");
                sendJson(exchange, 200, "{\"status\":\"applied\",\"event_id\":\"" + escape(eventId) + "\"}");
            }
        }

        private String validate(String eventId, String symbol, String transactionType, String quantityRaw) {
            if (eventId == null || eventId.isBlank()) return "event_id must be a non-empty string";
            if (symbol == null || symbol.isBlank()) return "symbol must be a non-empty string";
            if (!"BUY".equals(transactionType) && !"SELL".equals(transactionType)) {
                return "transaction_type must be exactly BUY or SELL";
            }
            if (quantityRaw == null || quantityRaw.isBlank()) return "quantity must be a positive integer";
            int qty;
            try {
                qty = Integer.parseInt(quantityRaw.trim());
            } catch (NumberFormatException e) {
                return "quantity must be a positive integer";
            }
            if (qty <= 0) return "quantity must be a positive integer";
            return null;
        }
    }

    private class PositionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, SimpleJson.errorJson("Method not allowed, use GET"));
                return;
            }
            Map<String, Integer> positions = store.getPositions();
            sendJson(exchange, 200, SimpleJson.toJsonObject(positions));
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}