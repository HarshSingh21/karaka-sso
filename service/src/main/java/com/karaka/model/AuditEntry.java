package com.karaka.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One line of the register's audit trail.
 *
 * <p>Immutable on purpose. An audit trail that can be edited after the fact is
 * not an audit trail, so this has no setters — unlike {@link Employee}, which is
 * mutable because it models something that genuinely changes.
 *
 * <p>{@code actor} is the Keycloak {@code preferred_username} of whoever made the
 * change, passed down from the controller. The service does not read it from the
 * security context itself: that would tie the service layer to Spring Security
 * and make it awkward to call from a scheduled job or an import.
 */
public record AuditEntry(
    long sequence, Instant at, String actor, String action, String subject, String detail) {

  public AuditEntry {
    Objects.requireNonNull(at, "at");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(subject, "subject");
  }
}
