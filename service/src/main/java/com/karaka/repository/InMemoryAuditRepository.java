package com.karaka.repository;

import com.karaka.model.AuditEntry;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

/**
 * In-memory {@link AuditRepository}.
 *
 * <p>Entries carry a monotonic {@code sequence} as well as a timestamp because
 * two changes inside the same millisecond are ordinary under load, and sorting by
 * {@link java.time.Instant} alone would present them in an arbitrary order.
 *
 * <p>Bounded at {@value #MAX_ENTRIES}. An unbounded collection on a long-running
 * process is a slow memory leak; a real deployment would write these to a
 * database or a log sink instead of keeping them at all.
 */
@Repository
@RequiredArgsConstructor
public class InMemoryAuditRepository implements AuditRepository {

  private static final int MAX_ENTRIES = 500;

  private final Queue<AuditEntry> entries = new ConcurrentLinkedQueue<>();
  private final AtomicLong sequence = new AtomicLong(0);
  private final Clock clock;


  @Override
  public AuditEntry append(String actor, String action, String subject, String detail) {
    var entry =
        new AuditEntry(sequence.incrementAndGet(), clock.instant(), actor, action, subject, detail);
    entries.add(entry);
    // Trim oldest-first. A while loop rather than a single poll because several
    // threads may append concurrently and push the size past the cap by more
    // than one.
    while (entries.size() > MAX_ENTRIES) {
      entries.poll();
    }
    return entry;
  }

  @Override
  public List<AuditEntry> findRecent(int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return entries.stream()
        .sorted(Comparator.comparingLong(AuditEntry::sequence).reversed())
        .limit(limit)
        .toList();
  }
}
