package com.karaka.orbit.controller;

import com.karaka.orbit.dto.BranchResponse;
import com.karaka.orbit.service.BranchService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Branch reference data, needed to populate the register's filters and forms. */
@RestController
@RequestMapping("/api/branches")
class BranchController {

  private final BranchService branches;

  BranchController(BranchService branches) {
    this.branches = branches;
  }

  @GetMapping
  @PreAuthorize("hasRole('ORBIT_VIEW')")
  List<BranchResponse> list() {
    return branches.findAll().stream().map(BranchResponse::from).toList();
  }
}
