package com.karaka.orbit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Invariants {@link Employee} defends on its own.
 *
 * <p>These are pure logic with no Spring context, and they cover the rules that exist
 * precisely so a service cannot get them wrong — setting a status without its date,
 * exiting someone twice, reinstating a leaver. Each one is a rule the API relies on but
 * that no HTTP test exercises directly.
 */
class EmployeeTest {

  private static final LocalDate JOINED = LocalDate.of(2020, 1, 15);

  private Employee active() {
    return new Employee(
        "OPL-0001", "Asha Rao", "Asha@Opal.example", "blr", "Head of People",
        EmploymentStatus.ACTIVE, JOINED);
  }

  @Test
  void normalisesEmailAndBranchOnConstruction() {
    Employee employee = active();
    // Uniqueness checks compare emails directly, so normalisation is what makes
    // "Asha@..." and "asha@..." the same person rather than two records.
    assertThat(employee.getEmail()).isEqualTo("asha@opal.example");
    assertThat(employee.getBranchCode()).isEqualTo("BLR");
  }

  @Test
  void exitSetsStatusAndDateTogether() {
    Employee employee = active();
    employee.exitOn(LocalDate.of(2024, 6, 30));

    assertThat(employee.getStatus()).isEqualTo(EmploymentStatus.EXITED);
    assertThat(employee.getExitedOn()).isEqualTo(LocalDate.of(2024, 6, 30));
  }

  @Test
  void refusesToExitTwice() {
    Employee employee = active();
    employee.exitOn(LocalDate.of(2024, 6, 30));

    // Allowing this would overwrite the original leaving date and lose it.
    assertThatIllegalStateException()
        .isThrownBy(() -> employee.exitOn(LocalDate.of(2025, 1, 1)))
        .withMessageContaining("already exited");
  }

  @Test
  void refusesAnExitBeforeJoining() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> active().exitOn(JOINED.minusDays(1)))
        .withMessageContaining("precedes joining date");
  }

  @Test
  void refusesToReinstateSomeoneWhoLeft() {
    Employee employee = active();
    employee.exitOn(LocalDate.of(2024, 6, 30));

    // Rehiring is a new record; editing this one would erase the fact they left.
    assertThatIllegalStateException()
        .isThrownBy(() -> employee.changeStatus(EmploymentStatus.ACTIVE))
        .withMessageContaining("new record");
  }

  @Test
  void refusesToExitViaChangeStatus() {
    // EXITED needs a date, so it must go through exitOn. Without this guard a caller
    // could produce a record marked exited with a null exit date.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> active().changeStatus(EmploymentStatus.EXITED))
        .withMessageContaining("exitOn");
  }

  @Test
  void movesBetweenActiveAndInactive() {
    Employee employee = active();
    employee.changeStatus(EmploymentStatus.INACTIVE);
    assertThat(employee.getStatus()).isEqualTo(EmploymentStatus.INACTIVE);

    employee.changeStatus(EmploymentStatus.ACTIVE);
    assertThat(employee.getStatus()).isEqualTo(EmploymentStatus.ACTIVE);
  }

  @Test
  void derivesUpToTwoInitials() {
    assertThat(active().initials()).isEqualTo("AR");

    Employee single = new Employee(
        "OPL-0002", "Madonna", "m@opal.example", "MUM", "Artist",
        EmploymentStatus.ACTIVE, JOINED);
    assertThat(single.initials()).isEqualTo("M");
  }

  @Test
  void identityIsTheIdAloneNotTheState() {
    Employee one = active();
    Employee two = new Employee(
        "OPL-0001", "Different Name", "other@opal.example", "MUM", "Other",
        EmploymentStatus.INACTIVE, JOINED);

    // Two loads of the same row are the same employee, whatever the field values.
    assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
  }

  @Test
  void rejectsNullsAtConstruction() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> new Employee(
            "OPL-0003", null, "x@opal.example", "BLR", "T", EmploymentStatus.ACTIVE, JOINED));
  }
}
