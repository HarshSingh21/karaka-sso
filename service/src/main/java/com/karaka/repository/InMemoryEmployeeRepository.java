package com.karaka.repository;

import com.karaka.model.Employee;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;
import com.karaka.utils.DataSeeder;

/**
 * In-memory {@link EmployeeRepository}.
 *
 * <p>A {@link ConcurrentHashMap} rather than a plain one because Tomcat serves
 * requests on many threads and this bean is a singleton — an unsynchronised
 * {@code HashMap} here can corrupt on concurrent writes, which shows up as a
 * hang or a lost record rather than a clean error.
 *
 * <p>Data is lost on restart, by design: the choice was an in-memory store so
 * the stack runs without a second database. {@code DataSeeder} refills it.
 */
@Repository
public class InMemoryEmployeeRepository implements EmployeeRepository {

  private static final String ID_PREFIX = "OPL-";

  private final Map<String, Employee> byId = new ConcurrentHashMap<>();
  private final AtomicInteger sequence = new AtomicInteger(0);

  @Override
  public List<Employee> findAll() {
    // Sorted so the register has a stable order; a ConcurrentHashMap's own
    // iteration order is unspecified and would shuffle rows between reloads.
    return byId.values().stream().sorted(Comparator.comparing(Employee::getId)).toList();
  }

  @Override
  public Optional<Employee> findById(String id) {
    return id == null ? Optional.empty() : Optional.ofNullable(byId.get(normaliseId(id)));
  }

  @Override
  public boolean existsByEmail(String email) {
    return findByEmail(email).isPresent();
  }

  @Override
  public boolean existsByEmailAndIdNot(String email, String excludedId) {
    String excluded = excludedId == null ? "" : normaliseId(excludedId);
    return findByEmail(email).filter(found -> !found.getId().equals(excluded)).isPresent();
  }

  @Override
  public Employee save(Employee employee) {
    byId.put(employee.getId(), employee);
    // Keep the generator ahead of any id that arrived from outside — the seeder
    // inserts OPL-0001..0006, and without this the first created employee would
    // be handed OPL-0001 again and silently overwrite the seeded row.
    sequence.accumulateAndGet(numericPart(employee.getId()), Math::max);
    return employee;
  }

  @Override
  public long count() {
    return byId.size();
  }

  @Override
  public String nextId() {
    return ID_PREFIX + String.format(Locale.ROOT, "%04d", sequence.incrementAndGet());
  }

  private Optional<Employee> findByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    String needle = email.strip().toLowerCase(Locale.ROOT);
    return byId.values().stream().filter(e -> e.getEmail().equals(needle)).findFirst();
  }

  private String normaliseId(String id) {
    return id.strip().toUpperCase(Locale.ROOT);
  }

  /** 0 for anything unparseable, so a hand-written id never breaks generation. */
  private int numericPart(String id) {
    int dash = id.lastIndexOf('-');
    if (dash < 0 || dash == id.length() - 1) {
      return 0;
    }
    try {
      return Integer.parseInt(id.substring(dash + 1));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }
}
