package com.karaka.service;

import com.karaka.exception.DuplicateEmailException;
import com.karaka.exception.EmployeeNotFoundException;
import com.karaka.exception.UnknownBranchException;
import com.karaka.model.Employee;
import com.karaka.model.enums.EmploymentStatus;
import com.karaka.repository.AuditRepository;
import com.karaka.repository.BranchRepository;
import com.karaka.repository.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.karaka.exception.GlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * Use cases for the employee register.
 *
 * <p>Holds the rules that span more than one object — "the branch must exist",
 * "the email must be unique", "every change is audited". Rules about a single
 * employee's own consistency live on {@link Employee} itself, so this class never
 * needs to set a status and a date together and hope both were remembered.
 *
 * <p>{@code actor} is passed in rather than read from the security context. That
 * keeps this class callable from a job or an import, and it keeps Spring Security
 * out of the service layer — the controller already knows who is calling.
 *
 * <p>No {@code @Transactional}: the store is an in-memory map, and the annotation
 * would imply atomicity that does not exist. Add it with the JPA repository, at
 * which point every method that writes more than once needs it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeService {

  private final EmployeeRepository employees;
  private final BranchRepository branches;
  private final AuditRepository audit;
  private final Clock clock;


  /**
   * Filtered register listing. Any argument may be null, meaning "no filter".
   *
   * <p>Filtering in memory is honest for a map-backed store. With JPA this
   * becomes a {@code Specification} so the database does the work — at which
   * point this method's signature does not change.
   */
  public List<Employee> findAll(String search, String branchCode, EmploymentStatus status) {
    String needle = search == null || search.isBlank()
        ? null
        : search.strip().toLowerCase(Locale.ROOT);
    String branch = branchCode == null || branchCode.isBlank()
        ? null
        : branchCode.strip().toUpperCase(Locale.ROOT);

    return employees.findAll().stream()
        .filter(employee -> branch == null || employee.getBranchCode().equals(branch))
        .filter(employee -> status == null || employee.getStatus() == status)
        .filter(employee -> needle == null || matches(employee, needle))
        .toList();
  }

  public Employee findById(String id) {
    return employees.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
  }

  public Employee create(EmployeeData data, String actor) {
    requireKnownBranch(data.branchCode());
    if (employees.existsByEmail(data.email())) {
      throw new DuplicateEmailException(data.email());
    }

    var employee = new Employee(
        employees.nextId(),
        data.fullName(),
        data.email(),
        data.branchCode(),
        data.title(),
        EmploymentStatus.ACTIVE,
        data.joinedOn() != null ? data.joinedOn() : LocalDate.now(clock));

    employees.save(employee);
    audit.append(actor, "EMPLOYEE_CREATED", employee.getId(),
        employee.getFullName() + " joined " + employee.getBranchCode());
    log.debug("{} created employee {}", actor, employee.getId());
    return employee;
  }

  public Employee update(String id, EmployeeData data, String actor) {
    Employee employee = findById(id);
    requireKnownBranch(data.branchCode());

    // Excludes this employee: keeping your own address is not a duplicate, and
    // a plain existsByEmail here would reject every no-op save.
    if (employees.existsByEmailAndIdNot(data.email(), employee.getId())) {
      throw new DuplicateEmailException(data.email());
    }

    String previousBranch = employee.getBranchCode();
    employee.rename(data.fullName());
    employee.changeEmail(data.email());
    employee.retitle(data.title());
    employee.transferTo(data.branchCode());
    employees.save(employee);

    // Transfers are what people actually go looking for in the trail, so they
    // get their own line rather than being folded into a generic "updated".
    if (!previousBranch.equals(employee.getBranchCode())) {
      audit.append(actor, "EMPLOYEE_TRANSFERRED", employee.getId(),
          previousBranch + " to " + employee.getBranchCode());
    }
    audit.append(actor, "EMPLOYEE_UPDATED", employee.getId(), employee.getFullName());
    return employee;
  }

  /**
   * Records that an employee has left.
   *
   * @param exitOn day they left; defaults to today when null
   */
  public Employee exit(String id, LocalDate exitOn, String actor) {
    Employee employee = findById(id);
    LocalDate effective = exitOn != null ? exitOn : LocalDate.now(clock);
    // Employee.exitOn enforces "not already exited" and "not before joining" —
    // both surface as 409/400 through GlobalExceptionHandler.
    employee.exitOn(effective);
    employees.save(employee);
    audit.append(actor, "EMPLOYEE_EXITED", employee.getId(), "effective " + effective);
    return employee;
  }

  /** Moves between ACTIVE and INACTIVE. Exiting goes through {@link #exit}. */
  public Employee changeStatus(String id, EmploymentStatus status, String actor) {
    Employee employee = findById(id);
    employee.changeStatus(status);
    employees.save(employee);
    audit.append(actor, "EMPLOYEE_STATUS_CHANGED", employee.getId(), status.name());
    return employee;
  }

  private void requireKnownBranch(String branchCode) {
    if (branchCode == null || !branches.existsByCode(branchCode)) {
      throw new UnknownBranchException(branchCode);
    }
  }

  private boolean matches(Employee employee, String lowercaseNeedle) {
    return employee.getFullName().toLowerCase(Locale.ROOT).contains(lowercaseNeedle)
        || employee.getEmail().contains(lowercaseNeedle)
        || employee.getId().toLowerCase(Locale.ROOT).contains(lowercaseNeedle)
        || employee.getTitle().toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
  }
}
