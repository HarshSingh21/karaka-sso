package com.karaka.utils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

/**
 * A sliding-window counter, keyed by whatever the caller chooses.
 *
 * <p>Exists because this application publishes an endpoint that confirms whether a username
 * exists. Keycloak's {@code bruteForceProtected} does not help: it counts failed
 * <em>logins</em>, and nothing here is a login. Unmetered, the endpoint is a roster-harvesting
 * API against a payroll and attendance system.
 *
 * <p>In memory and therefore per-instance, which is honest rather than ideal: two replicas
 * allow twice the traffic. That is acceptable while sessions are also in-process — both become
 * shared-state problems at the same moment, and a limiter that pretends otherwise would be
 * more misleading than one whose limitation is written down. Redis is the replacement.
 *
 * <p>Timestamps come from the injected {@link Clock} so a test can advance time instead of
 * sleeping through the window.
 */
@RequiredArgsConstructor
public final class SlidingWindowRateLimiter {

  private final Clock clock;
  private final int maxEvents;
  private final Duration window;
  private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();


  /**
   * Records an attempt for {@code key}.
   *
   * @return true when the attempt is allowed; false when {@code key} is over its limit
   */
  public boolean tryAcquire(String key) {
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);
    // compute() rather than get-then-put: the whole read-evict-append must be atomic for the
    // key, or two concurrent requests both observe maxEvents-1 and both pass.
    Deque<Instant> timestamps =
        hits.compute(
            key,
            (k, existing) -> {
              Deque<Instant> deque = existing == null ? new ArrayDeque<>() : existing;
              while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
              }
              if (deque.size() < maxEvents) {
                deque.addLast(now);
              }
              return deque;
            });
    // Over the limit iff the window is full and the newest entry is not the one just added.
    return timestamps.size() < maxEvents || timestamps.peekLast() == now;
  }

  /**
   * Drops keys with no activity inside the window, so the map cannot grow without bound as
   * unique caller IPs accumulate. Called on a schedule, not per request.
   */
  public void evictIdle() {
    Instant cutoff = clock.instant().minus(window);
    hits.entrySet()
        .removeIf(
            entry -> {
              Deque<Instant> deque = entry.getValue();
              synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                  deque.pollFirst();
                }
                return deque.isEmpty();
              }
            });
  }
}
