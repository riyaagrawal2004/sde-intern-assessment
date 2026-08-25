package com.indothai.position;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe in-memory store for net positions per symbol.
 *
 * Responsibilities:
 * - Track net position per symbol (BUY adds, SELL subtracts).
 * - Enforce idempotency: first valid event for an event_id wins,
 *   later duplicates with the same event_id are ignored.
 * - Stay correct when position updates (writes) and GET /position
 *   reads happen at the same time (concurrency requirement in the PDF).
 */
public class PositionStore {

    private final Map<String, Integer> positions = new HashMap<>();
    private final Set<String> seenEventIds = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    public enum ApplyResult { APPLIED, DUPLICATE }

    /**
     * Applies one validated event to the store.
     * Assumes the event already passed the Event Contract validation
     * (that check happens in the Order Update Service / RowValidator,
     * and is re-checked defensively at the HTTP layer here too).
     */
    public ApplyResult applyEvent(String eventId, String symbol, String transactionType, int quantity) {
        lock.lock();
        try {
            if (seenEventIds.contains(eventId)) {
                return ApplyResult.DUPLICATE;
            }
            seenEventIds.add(eventId);

            int delta = transactionType.equals("BUY") ? quantity : -quantity;
            int current = positions.getOrDefault(symbol, 0);
            positions.put(symbol, current + delta);

            return ApplyResult.APPLIED;
        } finally {
            lock.unlock();
        }
    }

    /** Returns a snapshot copy of all symbol positions. */
    public Map<String, Integer> getPositions() {
        lock.lock();
        try {
            return new HashMap<>(positions);
        } finally {
            lock.unlock();
        }
    }

    public boolean hasSeen(String eventId) {
        lock.lock();
        try {
            return seenEventIds.contains(eventId);
        } finally {
            lock.unlock();
        }
    }
}