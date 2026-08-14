package com.karaka.repository;

import com.karaka.model.Branch;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for branches. */
public interface BranchRepository {

  List<Branch> findAll();

  Optional<Branch> findByCode(String code);

  boolean existsByCode(String code);

  Branch save(Branch branch);
}
