package com.indothai.orderupdate;

/**
 * Represents one validated order event, ready to be sent to the
 * Position Maintaining Service.
 */
public class OrderEvent {
    public final String eventId;
    public final String symbol;
    public final String transactionType; // "BUY" or "SELL"
    public final int quantity;

    public OrderEvent(String eventId, String symbol, String transactionType, int quantity) {
        this.eventId = eventId;
        this.symbol = symbol;
        this.transactionType = transactionType;
        this.quantity = quantity;
    }

    /** Serializes this event to a flat JSON object for the /events POST body. */
    public String toJson() {
        return "{"
                + "\"event_id\":\"" + escape(eventId) + "\","
                + "\"symbol\":\"" + escape(symbol) + "\","
                + "\"transaction_type\":\"" + escape(transactionType) + "\","
                + "\"quantity\":" + quantity
                + "}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}