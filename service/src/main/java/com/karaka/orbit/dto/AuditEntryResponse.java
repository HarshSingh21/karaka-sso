package com.karaka.orbit.dto;

import com.karaka.orbit.model.AuditEntry;
import java.time.Instant;

/** One audit-trail line as the API returns it. */
public record AuditEntryResponse(
    long sequence, Instant at, String actor, String action, String subject, String detail) {

  public static AuditEntryResponse from(AuditEntry entry) {
    return new AuditEntryResponse(
        entry.sequence(),
        entry.at(),
        entry.actor(),
        entry.action(),
        entry.subject(),
        entry.detail());
  }
}
