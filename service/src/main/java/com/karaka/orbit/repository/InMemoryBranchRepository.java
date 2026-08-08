package com.karaka.orbit.repository;

import com.karaka.orbit.model.Branch;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory {@link BranchRepository}. */
@Repository
class InMemoryBranchRepository implements BranchRepository {

  private final Map<String, Branch> byCode = new ConcurrentHashMap<>();

  @Override
  public List<Branch> findAll() {
    return byCode.values().stream().sorted(Comparator.comparing(Branch::getCode)).toList();
  }

  @Override
  public Optional<Branch> findByCode(String code) {
    return code == null || code.isBlank()
        ? Optional.empty()
        : Optional.ofNullable(byCode.get(normalise(code)));
  }

  @Override
  public boolean existsByCode(String code) {
    return findByCode(code).isPresent();
  }

  @Override
  public Branch save(Branch branch) {
    byCode.put(branch.getCode(), branch);
    return branch;
  }

  private String normalise(String code) {
    return code.strip().toUpperCase(Locale.ROOT);
  }
}
