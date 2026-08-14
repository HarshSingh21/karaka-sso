package com.karaka.exception;

/** Thrown when an employee id does not resolve. Mapped to HTTP 404. */
public class EmployeeNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String employeeId;

  public EmployeeNotFoundException(String employeeId) {
    super("No employee with id " + employeeId);
    this.employeeId = employeeId;
  }

  public String getEmployeeId() {
    return employeeId;
  }
}
