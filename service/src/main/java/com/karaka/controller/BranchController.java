package com.karaka.controller;

import com.karaka.dto.BranchResponse;
import com.karaka.service.BranchService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.karaka.model.Branch;
import lombok.RequiredArgsConstructor;

/** Branch reference data, needed to populate the register's filters and forms. */
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

  private final BranchService branches;


  @GetMapping
  @PreAuthorize("hasRole('ORBIT_VIEW')")
  List<BranchResponse> list() {
    return branches.findAll().stream().map(BranchResponse::from).toList();
  }
}
