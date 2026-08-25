package com.indothai.orderupdate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Sends validated OrderEvents to the Position Maintaining Service over HTTP.
 *
 * Delivery characteristics (documented here for the README too):
 *  - At-most-once, best-effort per event: one HTTP POST per event, no retry
 *    queue, no durable delivery -- explicitly out of scope per the PDF.
 *  - Connection/timeout/non-2xx errors are caught and logged with the
 *    event_id, then processing continues with the next row -- a single
 *    failed send must not stop the rest of the file from being processed.
 */
public class PositionServiceClient {

    private static final Logger LOGGER = Logger.getLogger(PositionServiceClient.class.getName());

    private final HttpClient httpClient;
    private final URI eventsUri;

    public PositionServiceClient(String positionServiceBaseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.eventsUri = URI.create(positionServiceBaseUrl + "/events");
    }

    /**
     * Sends one event. Returns true if the Position Service accepted it
     * (HTTP 2xx), false otherwise. Never throws -- all failures are caught
     * and logged so the caller can keep processing subsequent rows.
     */
    public boolean send(OrderEvent event) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(eventsUri)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.toJson()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.fine(() -> "Sent event " + event.eventId + " -> " + response.body());
                return true;
            } else {
                LOGGER.warning("Position Service rejected event " + event.eventId
                        + " with status " + response.statusCode() + ": " + response.body());
                return false;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to send event " + event.eventId + " (connection error): " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Interrupted while sending event " + event.eventId);
            return false;
        }
    }
}