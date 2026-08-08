package com.karaka.orbit.service;

import com.karaka.orbit.model.Branch;
import com.karaka.orbit.repository.BranchRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read access to branches.
 *
 * <p>Thin on purpose. Branches are reference data in this release — created by
 * the seeder, not by users — so there is no create/update path to guard yet. It
 * exists as a seam: when branch administration arrives, the rules land here
 * rather than in a controller.
 */
@Service
public class BranchService {

  private final BranchRepository branches;

  BranchService(BranchRepository branches) {
    this.branches = branches;
  }

  public List<Branch> findAll() {
    return branches.findAll();
  }
}
