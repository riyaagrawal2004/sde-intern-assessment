package com.indothai.position;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class PositionHttpServerTest {

    private PositionHttpServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        // Port 0 = let the OS pick a free port, so tests never collide with
        // a manually-running instance or with each other.
        PositionStore store = new PositionStore();
        server = new PositionHttpServer(store, "localhost", 0);
        server.start();
        int actualPort = server.getAddress().getPort();
        baseUrl = "http://localhost:" + actualPort;

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getPositionReturnsEmptyObjectInitially() throws Exception {
        HttpResponse<String> response = get("/position");

        assertEquals(200, response.statusCode());
        assertEquals("{}", response.body());
    }

    @Test
    void postEventThenGetPositionReflectsIt() throws Exception {
        HttpResponse<String> postResponse = post("/events",
                "{\"event_id\":\"evt-1\",\"symbol\":\"RELIANCE\",\"transaction_type\":\"BUY\",\"quantity\":90}");
        assertEquals(200, postResponse.statusCode());
        assertTrue(postResponse.body().contains("\"applied\""));

        HttpResponse<String> getResponse = get("/position");
        assertEquals(200, getResponse.statusCode());
        assertTrue(getResponse.body().contains("\"RELIANCE\":90"));
    }

    @Test
    void multipleSymbolsAllAppearInPositionResponse() throws Exception {
        post("/events", "{\"event_id\":\"evt-1\",\"symbol\":\"RELIANCE\",\"transaction_type\":\"BUY\",\"quantity\":90}");
        post("/events", "{\"event_id\":\"evt-2\",\"symbol\":\"TCS\",\"transaction_type\":\"SELL\",\"quantity\":75}");

        HttpResponse<String> getResponse = get("/position");

        String body = getResponse.body();
        assertTrue(body.contains("\"RELIANCE\":90"));
        assertTrue(body.contains("\"TCS\":-75"));
    }

    @Test
    void duplicateEventIdViaHttpIsIgnored() throws Exception {
        post("/events", "{\"event_id\":\"evt-1\",\"symbol\":\"RELIANCE\",\"transaction_type\":\"BUY\",\"quantity\":90}");
        HttpResponse<String> secondPost = post("/events",
                "{\"event_id\":\"evt-1\",\"symbol\":\"RELIANCE\",\"transaction_type\":\"BUY\",\"quantity\":500}");

        assertEquals(200, secondPost.statusCode());
        assertTrue(secondPost.body().contains("\"duplicate\""));

        HttpResponse<String> getResponse = get("/position");
        assertTrue(getResponse.body().contains("\"RELIANCE\":90")); // unaffected by duplicate
    }

    @Test
    void invalidTransactionTypeViaHttpIsRejectedWith400() throws Exception {
        HttpResponse<String> response = post("/events",
                "{\"event_id\":\"evt-1\",\"symbol\":\"RELIANCE\",\"transaction_type\":\"HOLD\",\"quantity\":90}");

        assertEquals(400, response.statusCode());
    }

    @Test
    void malformedJsonBodyDoesNotCrashServer() throws Exception {
        HttpResponse<String> response = post("/events", "not valid json at all");

        assertEquals(400, response.statusCode());

        // Server must still be responsive after malformed input.
        HttpResponse<String> getResponse = get("/position");
        assertEquals(200, getResponse.statusCode());
    }

    @Test
    void getOnEventsPathIsRejected() throws Exception {
        HttpResponse<String> response = get("/events");

        assertEquals(405, response.statusCode());
    }
}