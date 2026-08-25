package com.indothai.orderupdate;

/**
 * Simple, configurable rate limiter: ensures events are emitted at no
 * more than a configured rate (default 50/sec).
 *
 * Design: sleep-based pacing, not a precise token bucket -- the PDF
 * explicitly says "Exact sub-millisecond timing is not expected; a clear
 * and configurable throttle is sufficient" and warns against flaky
 * timing-dependent tests, so a simple, predictable approach is preferred
 * over a more complex (and harder to test) precise limiter.
 */
public class Throttler {

    private final long minIntervalMillis;
    private long lastEmitTimeMillis = 0;

    public Throttler(int maxEventsPerSecond) {
        if (maxEventsPerSecond <= 0) {
            throw new IllegalArgumentException("maxEventsPerSecond must be positive");
        }
        this.minIntervalMillis = 1000L / maxEventsPerSecond;
    }

    /**
     * Blocks (sleeps) just long enough to respect the configured rate,
     * then returns. Call this once before emitting each event.
     */
    public void throttle() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastEmitTimeMillis;
        long waitTime = minIntervalMillis - elapsed;
        if (waitTime > 0) {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastEmitTimeMillis = System.currentTimeMillis();
    }
}