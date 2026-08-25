package com.indothai.position;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PositionStoreTest {

    private PositionStore store;

    @BeforeEach
    void setUp() {
        store = new PositionStore();
    }

    @Test
    void buyIncreasesPosition() {
        PositionStore.ApplyResult result = store.applyEvent("evt-1", "RELIANCE", "BUY", 90);

        assertEquals(PositionStore.ApplyResult.APPLIED, result);
        assertEquals(90, store.getPositions().get("RELIANCE"));
    }

    @Test
    void sellDecreasesPosition() {
        store.applyEvent("evt-1", "TCS", "BUY", 100);
        store.applyEvent("evt-2", "TCS", "SELL", 75);

        assertEquals(25, store.getPositions().get("TCS"));
    }

    @Test
    void sellCanMakePositionNegative() {
        store.applyEvent("evt-1", "TCS", "SELL", 75);

        assertEquals(-75, store.getPositions().get("TCS"));
    }

    @Test
    void multipleSymbolsTrackedIndependently() {
        store.applyEvent("evt-1", "RELIANCE", "BUY", 90);
        store.applyEvent("evt-2", "TCS", "SELL", 75);
        store.applyEvent("evt-3", "RELIANCE", "SELL", 40);

        Map<String, Integer> positions = store.getPositions();
        assertEquals(50, positions.get("RELIANCE"));
        assertEquals(-75, positions.get("TCS"));
    }

    @Test
    void netPositionCanBeExactlyZero() {
        store.applyEvent("evt-1", "INFY", "BUY", 30);
        store.applyEvent("evt-2", "INFY", "SELL", 30);

        assertEquals(0, store.getPositions().get("INFY"));
    }

    @Test
    void duplicateEventIdIsIgnored() {
        store.applyEvent("evt-1", "RELIANCE", "BUY", 90);
        PositionStore.ApplyResult secondResult = store.applyEvent("evt-1", "RELIANCE", "BUY", 500);

        assertEquals(PositionStore.ApplyResult.DUPLICATE, secondResult);
        // Position should be unaffected by the duplicate -- still 90, not 590.
        assertEquals(90, store.getPositions().get("RELIANCE"));
    }

    @Test
    void duplicateEventIdIgnoredEvenIfOtherFieldsDiffer() {
        // Contract: "first valid event for an event_id wins, ignore later
        // events with the same ID, even if another field differs."
        store.applyEvent("evt-1", "RELIANCE", "BUY", 90);
        PositionStore.ApplyResult secondResult = store.applyEvent("evt-1", "TCS", "SELL", 10);

        assertEquals(PositionStore.ApplyResult.DUPLICATE, secondResult);
        assertEquals(90, store.getPositions().get("RELIANCE"));
        assertNull(store.getPositions().get("TCS")); // TCS was never actually applied
    }

    @Test
    void getPositionsIncludesAllSymbolsSeenEvenWithZeroPosition() {
        store.applyEvent("evt-1", "INFY", "BUY", 30);
        store.applyEvent("evt-2", "INFY", "SELL", 30);
        store.applyEvent("evt-3", "TCS", "BUY", 10);

        Map<String, Integer> positions = store.getPositions();
        assertTrue(positions.containsKey("INFY"));
        assertEquals(0, positions.get("INFY"));
        assertTrue(positions.containsKey("TCS"));
    }

    @Test
    void emptyStoreReturnsEmptyPositions() {
        assertTrue(store.getPositions().isEmpty());
    }
}