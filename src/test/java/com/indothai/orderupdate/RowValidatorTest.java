package com.indothai.orderupdate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RowValidatorTest {

    private final RowValidator validator = new RowValidator();

    @Test
    void validRowIsAccepted() {
        RowValidator.Result result = validator.validate("evt-0001", "RELIANCE", "BUY", "90");

        assertTrue(result.isValid());
        assertEquals("evt-0001", result.event.eventId);
        assertEquals("RELIANCE", result.event.symbol);
        assertEquals("BUY", result.event.transactionType);
        assertEquals(90, result.event.quantity);
    }

    @Test
    void sellRowIsAccepted() {
        RowValidator.Result result = validator.validate("evt-0002", "TCS", "SELL", "75");

        assertTrue(result.isValid());
        assertEquals("SELL", result.event.transactionType);
    }

    @Test
    void symbolCaseAndValueArePreservedExactly() {
        RowValidator.Result result = validator.validate("evt-1", "reliance", "BUY", "10");

        assertTrue(result.isValid());
        assertEquals("reliance", result.event.symbol); // not upper-cased or altered
    }

    // --- blank event_id ---

    @Test
    void blankEventIdIsRejected() {
        RowValidator.Result result = validator.validate("", "RELIANCE", "BUY", "90");

        assertFalse(result.isValid());
        assertNotNull(result.rejectionReason);
    }

    @Test
    void nullEventIdIsRejected() {
        RowValidator.Result result = validator.validate(null, "RELIANCE", "BUY", "90");

        assertFalse(result.isValid());
    }

    @Test
    void whitespaceOnlyEventIdIsRejected() {
        RowValidator.Result result = validator.validate("   ", "RELIANCE", "BUY", "90");

        assertFalse(result.isValid());
    }

    // --- blank symbol ---

    @Test
    void blankSymbolIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "", "BUY", "90");

        assertFalse(result.isValid());
    }

    @Test
    void nullSymbolIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", null, "BUY", "90");

        assertFalse(result.isValid());
    }

    // --- invalid transaction_type ---

    @Test
    void invalidTransactionTypeIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "HOLD", "90");

        assertFalse(result.isValid());
    }

    @Test
    void lowercaseTransactionTypeIsRejected() {
        // Contract: "transaction_type must be exactly BUY or SELL" -- case-sensitive.
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "buy", "90");

        assertFalse(result.isValid());
    }

    @Test
    void blankTransactionTypeIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "", "90");

        assertFalse(result.isValid());
    }

    // --- quantity: zero, negative, non-integer, blank ---

    @Test
    void zeroQuantityIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "BUY", "0");

        assertFalse(result.isValid());
    }

    @Test
    void negativeQuantityIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "SELL", "-5");

        assertFalse(result.isValid());
    }

    @Test
    void nonIntegerDecimalQuantityIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "BUY", "10.5");

        assertFalse(result.isValid());
    }

    @Test
    void nonNumericQuantityIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "BUY", "abc");

        assertFalse(result.isValid());
    }

    @Test
    void blankQuantityIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "BUY", "");

        assertFalse(result.isValid());
    }

    @Test
    void nullQuantityIsRejected() {
        RowValidator.Result result = validator.validate("evt-1", "RELIANCE", "BUY", null);

        assertFalse(result.isValid());
    }

    // --- rejection reason should always be present and non-empty ---

    @Test
    void rejectionAlwaysIncludesAReason() {
        RowValidator.Result result = validator.validate("", "", "INVALID", "-1");

        assertFalse(result.isValid());
        assertNotNull(result.rejectionReason);
        assertFalse(result.rejectionReason.isBlank());
    }
}