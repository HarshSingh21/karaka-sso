package com.karaka.orbit.repository;

import com.karaka.orbit.model.AuditEntry;
import java.util.List;

/** Append-only store for the register's audit trail. */
public interface AuditRepository {

  /**
   * Appends an entry.
   *
   * <p>No {@code update} or {@code delete} on this interface — an audit trail
   * that supports either is not evidence of anything. Retention is a separate
   * concern from the write path.
   */
  AuditEntry append(String actor, String action, String subject, String detail);

  /** Most recent first, capped at {@code limit}. */
  List<AuditEntry> findRecent(int limit);
}
