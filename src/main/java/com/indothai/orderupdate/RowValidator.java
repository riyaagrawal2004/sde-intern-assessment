package com.indothai.orderupdate;

/**
 * Validates a single raw CSV row against the Event Contract:
 *  - event_id: non-empty string, unique (uniqueness/dedup is enforced
 *    downstream by the Position Maintaining Service, not here).
 *  - symbol: non-empty string, case preserved.
 *  - transaction_type: exactly "BUY" or "SELL" (case-sensitive as given).
 *  - quantity: a positive integer (no decimals, no blanks).
 *
 * Returns a Result that is either a valid OrderEvent or a rejection reason,
 * so the caller (CsvReader/OrderUpdateServiceMain) can log accordingly and
 * keep processing subsequent rows without stopping (PDF requirement).
 */
public class RowValidator {

    public static class Result {
        public final OrderEvent event;   // null if invalid
        public final String rejectionReason; // null if valid

        private Result(OrderEvent event, String rejectionReason) {
            this.event = event;
            this.rejectionReason = rejectionReason;
        }

        public static Result valid(OrderEvent event) {
            return new Result(event, null);
        }

        public static Result invalid(String reason) {
            return new Result(null, reason);
        }

        public boolean isValid() {
            return event != null;
        }
    }

    public Result validate(String eventId, String symbol, String transactionType, String quantityRaw) {
        if (eventId == null || eventId.isBlank()) {
            return Result.invalid("event_id must be a non-empty string");
        }
        if (symbol == null || symbol.isBlank()) {
            return Result.invalid("symbol must be a non-empty string");
        }
        if (transactionType == null || !(transactionType.equals("BUY") || transactionType.equals("SELL"))) {
            return Result.invalid("transaction_type must be exactly BUY or SELL, got: " + transactionType);
        }
        if (quantityRaw == null || quantityRaw.isBlank()) {
            return Result.invalid("quantity must be a positive integer, got blank");
        }

        int quantity;
        try {
            // Reject decimals like "10.5" -- Integer.parseInt already does this,
            // since it throws on any non-integer characters.
            quantity = Integer.parseInt(quantityRaw.trim());
        } catch (NumberFormatException e) {
            return Result.invalid("quantity must be a positive integer, got: " + quantityRaw);
        }

        if (quantity <= 0) {
            return Result.invalid("quantity must be positive, got: " + quantity);
        }

        return Result.valid(new OrderEvent(eventId.trim(), symbol, transactionType, quantity));
    }
}