package com.karaka.orbit.service;

import com.karaka.orbit.model.AuditEntry;
import com.karaka.orbit.repository.AuditRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read access to the audit trail.
 *
 * <p>Read only. Writes happen inside {@link EmployeeService} as part of the change
 * being recorded — an audit method callable on its own invites a caller to log
 * something that did not happen, or to change something and forget to log it.
 */
@Service
public class AuditService {

  /** Caps an unbounded {@code ?limit=} so one request cannot ask for everything. */
  private static final int MAX_LIMIT = 200;
  private static final int DEFAULT_LIMIT = 50;

  private final AuditRepository audit;

  AuditService(AuditRepository audit) {
    this.audit = audit;
  }

  public List<AuditEntry> findRecent(Integer requestedLimit) {
    int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
    return audit.findRecent(limit);
  }
}
