package com.karaka.orbit.controller;

import com.karaka.orbit.dto.AuditEntryResponse;
import com.karaka.orbit.service.AuditService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The register's audit trail.
 *
 * <p>Guarded by {@code ORBIT_AUDIT} rather than {@code ORBIT_MANAGE}. The trail says
 * who changed what, which is a different sensitivity from the directory itself and
 * from the ability to edit it: someone who maintains employee records all day has no
 * particular need to review their colleagues' edit history. Separating the two is
 * what makes {@code ORBIT_SUBADMIN} (maintains records) meaningfully weaker than
 * {@code ORBIT_ADMIN} (also reviews the trail).
 */
@RestController
@RequestMapping("/api/audit")
@Validated
class AuditController {

  private final AuditService audit;

  AuditController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping
  @PreAuthorize("hasRole('ORBIT_AUDIT')")
  List<AuditEntryResponse> recent(
      @RequestParam(required = false) @Positive(message = "limit must be greater than zero")
          Integer limit) {
    return audit.findRecent(limit).stream().map(AuditEntryResponse::from).toList();
  }
}
