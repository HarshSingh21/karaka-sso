package com.karaka.orbit.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * An employee in the ORBIT register.
 *
 * <p>Shaped to become a JPA {@code @Entity} without restructuring: mutable
 * fields, a protected no-arg constructor for the proxying Hibernate needs, and
 * {@link #equals(Object)} based on the identifier alone. Swapping the in-memory
 * repository for Spring Data means adding annotations to this class, not
 * rewriting it or the service above it.
 *
 * <p>Behaviour that enforces a rule lives here rather than in the service — see
 * {@link #exitOn(LocalDate)}. A service that reached in and set the status and
 * date separately could leave a record exited with no exit date, and every
 * caller would have to remember to set both.
 */
public class Employee {

  private String id;
  private String fullName;
  private String email;
  private String branchCode;
  private String title;
  private EmploymentStatus status;
  private LocalDate joinedOn;

  /** Set only when {@link #status} is {@link EmploymentStatus#EXITED}. */
  private LocalDate exitedOn;

  /** For frameworks (and future JPA). Application code uses the public constructor. */
  protected Employee() {}

  public Employee(
      String id,
      String fullName,
      String email,
      String branchCode,
      String title,
      EmploymentStatus status,
      LocalDate joinedOn) {
    this.id = Objects.requireNonNull(id, "id");
    this.fullName = Objects.requireNonNull(fullName, "fullName").strip();
    // Lower-cased on the way in so the uniqueness check cannot be defeated by
    // capitalisation: Asha@… and asha@… are one person.
    this.email = Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
    this.branchCode = Objects.requireNonNull(branchCode, "branchCode").strip().toUpperCase(Locale.ROOT);
    this.title = Objects.requireNonNull(title, "title").strip();
    this.status = Objects.requireNonNull(status, "status");
    this.joinedOn = Objects.requireNonNull(joinedOn, "joinedOn");
  }

  /**
   * Marks this employee as having left.
   *
   * @throws IllegalStateException if already exited — exiting twice would
   *     overwrite the original leaving date and lose it
   * @throws IllegalArgumentException if the date precedes the joining date
   */
  public void exitOn(LocalDate date) {
    Objects.requireNonNull(date, "exit date");
    if (status.isTerminal()) {
      throw new IllegalStateException(id + " has already exited on " + exitedOn);
    }
    if (date.isBefore(joinedOn)) {
      throw new IllegalArgumentException(
          "exit date " + date + " precedes joining date " + joinedOn);
    }
    this.status = EmploymentStatus.EXITED;
    this.exitedOn = date;
  }

  /**
   * Moves between {@link EmploymentStatus#ACTIVE} and
   * {@link EmploymentStatus#INACTIVE}.
   *
   * @throws IllegalStateException if the employee has exited — rehiring is a new
   *     record, otherwise the register forgets that they ever left
   */
  public void changeStatus(EmploymentStatus next) {
    Objects.requireNonNull(next, "status");
    if (status.isTerminal()) {
      throw new IllegalStateException(id + " has exited; reinstating requires a new record");
    }
    if (next.isTerminal()) {
      throw new IllegalArgumentException("use exitOn(date) to exit an employee");
    }
    this.status = next;
  }

  public void transferTo(String newBranchCode) {
    this.branchCode = Objects.requireNonNull(newBranchCode, "branchCode").strip().toUpperCase(Locale.ROOT);
  }

  public void rename(String newFullName) {
    this.fullName = Objects.requireNonNull(newFullName, "fullName").strip();
  }

  public void retitle(String newTitle) {
    this.title = Objects.requireNonNull(newTitle, "title").strip();
  }

  public void changeEmail(String newEmail) {
    this.email = Objects.requireNonNull(newEmail, "email").strip().toLowerCase(Locale.ROOT);
  }

  /** Up to two initials, for the avatar badge in the register table. */
  public String initials() {
    String[] parts = fullName.split("\\s+");
    StringBuilder out = new StringBuilder(2);
    for (String part : parts) {
      if (!part.isEmpty() && out.length() < 2) {
        out.append(Character.toUpperCase(part.charAt(0)));
      }
    }
    return out.isEmpty() ? "?" : out.toString();
  }

  public String getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public String getBranchCode() {
    return branchCode;
  }

  public String getTitle() {
    return title;
  }

  public EmploymentStatus getStatus() {
    return status;
  }

  public LocalDate getJoinedOn() {
    return joinedOn;
  }

  public LocalDate getExitedOn() {
    return exitedOn;
  }

  /** Identity, not state: two loads of the same row are the same employee. */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Employee that && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return "Employee[" + id + ", " + fullName + ", " + status + "]";
  }
}
