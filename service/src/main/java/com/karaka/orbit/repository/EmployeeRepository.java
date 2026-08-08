package com.karaka.orbit.repository;

import com.karaka.orbit.model.Employee;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for employees.
 *
 * <p>The method names are deliberately the ones Spring Data would derive
 * ({@code findById}, {@code existsByEmail}, {@code save}), so
 * moving to a real database is:
 *
 * <pre>{@code
 * public interface EmployeeRepository extends JpaRepository<Employee, String> { }
 * }</pre>
 *
 * <p>…and deleting the in-memory implementation. Nothing in the service or
 * controller layer changes, because neither knows which one it is talking to.
 */
public interface EmployeeRepository {

  List<Employee> findAll();

  Optional<Employee> findById(String id);

  boolean existsByEmail(String email);

  /**
   * Whether any employee <em>other than</em> {@code excludedId} uses this email.
   * Needed by update: an employee keeping their own address is not a duplicate.
   */
  boolean existsByEmailAndIdNot(String email, String excludedId);

  Employee save(Employee employee);

  long count();

  /**
   * Next free identifier, e.g. {@code OPL-0007}.
   *
   * <p>On the repository rather than the service because only the store knows
   * what is already taken. With a real database this becomes a sequence.
   */
  String nextId();
}
