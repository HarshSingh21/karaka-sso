package com.karaka.orbit.bootstrap;

import com.karaka.orbit.model.Branch;
import com.karaka.orbit.model.Employee;
import com.karaka.orbit.model.EmploymentStatus;
import com.karaka.orbit.repository.BranchRepository;
import com.karaka.orbit.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fills the in-memory store on startup.
 *
 * <p>Necessary rather than convenient: the repositories keep nothing across a
 * restart, so without this every run begins with an empty register and the
 * {@code .empty-state} placeholder, which makes the UI impossible to judge.
 *
 * <p>Seeds one {@link EmploymentStatus#INACTIVE} and one
 * {@link EmploymentStatus#EXITED} employee on purpose — a table where every row is
 * green never exercises the badge styles or the status filter.
 *
 * <p>The first few names deliberately match the Keycloak demo accounts in
 * {@code karaka-realm.json}, so that signing in as {@code ankit} and finding Ankit
 * Sharma in the register reads as one coherent organisation. They are still two
 * separate things — a login account and a row in an employee directory — and
 * nothing links them beyond the coincidence of the name.
 *
 * <p>Writes through the repositories rather than reaching into their maps, so the id
 * generator stays in step and a later JPA swap needs no change here.
 */
@Component
class DataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final EmployeeRepository employees;
  private final BranchRepository branches;

  DataSeeder(EmployeeRepository employees, BranchRepository branches) {
    this.employees = employees;
    this.branches = branches;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (employees.count() > 0) {
      // Guards against a devtools restart re-running this and duplicating rows.
      log.debug("Register already populated ({} employees); skipping seed", employees.count());
      return;
    }

    seedBranches();
    seedEmployees();
    log.info(
        "Seeded ORBIT register: {} employees across {} branches",
        employees.count(),
        branches.findAll().size());
  }

  private void seedBranches() {
    List.of(
            new Branch("BLR", "Bengaluru HQ", "Bengaluru", LocalDate.of(2016, 4, 1)),
            new Branch("MUM", "Mumbai West", "Mumbai", LocalDate.of(2018, 7, 15)),
            new Branch("DEL", "Delhi North", "New Delhi", LocalDate.of(2019, 11, 4)),
            new Branch("PNQ", "Pune Annexe", "Pune", LocalDate.of(2022, 2, 21)))
        .forEach(branches::save);
  }

  private void seedEmployees() {
    save("Ankit Sharma", "ankit@opal.example", "BLR", "Head of People",
        EmploymentStatus.ACTIVE, LocalDate.of(2017, 1, 9), null);
    save("Harsh Singh", "harsh@opal.example", "BLR", "Finance Controller",
        EmploymentStatus.ACTIVE, LocalDate.of(2018, 3, 12), null);
    save("Monti Verma", "monti@opal.example", "MUM", "Branch Manager",
        EmploymentStatus.ACTIVE, LocalDate.of(2019, 8, 1), null);
    save("Meera Iyer", "meera@opal.example", "DEL", "Accounts Executive",
        EmploymentStatus.ACTIVE, LocalDate.of(2021, 6, 14), null);
    // On extended leave — exercises the INACTIVE badge and filter.
    save("Farhan Qureshi", "farhan@opal.example", "PNQ", "Field Supervisor",
        EmploymentStatus.INACTIVE, LocalDate.of(2022, 9, 5), null);
    // Left the company — exercises the EXITED badge and the exit date column.
    save("Lakshmi Nair", "lakshmi@opal.example", "MUM", "Payroll Officer",
        EmploymentStatus.EXITED, LocalDate.of(2020, 2, 3), LocalDate.of(2024, 12, 31));
  }

  /**
   * Builds an employee in a given end state.
   *
   * <p>Constructed ACTIVE and then transitioned through the model's own methods
   * rather than injecting a status directly, so seeded data is reachable by the same
   * rules the API enforces. If a transition is ever made stricter this fails loudly
   * at startup instead of quietly seeding a record the API could not have produced.
   */
  private void save(
      String fullName,
      String email,
      String branchCode,
      String title,
      EmploymentStatus status,
      LocalDate joinedOn,
      LocalDate exitedOn) {

    var employee = new Employee(
        employees.nextId(), fullName, email, branchCode, title, EmploymentStatus.ACTIVE, joinedOn);

    switch (status) {
      case INACTIVE -> employee.changeStatus(EmploymentStatus.INACTIVE);
      case EXITED -> employee.exitOn(exitedOn);
      case ACTIVE -> {
        // Already active.
      }
    }
    employees.save(employee);
  }
}
