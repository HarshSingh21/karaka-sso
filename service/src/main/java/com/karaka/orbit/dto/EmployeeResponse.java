package com.karaka.orbit.dto;

import com.karaka.orbit.model.Employee;
import com.karaka.orbit.model.EmploymentStatus;
import java.time.LocalDate;

/**
 * An employee as the API returns them.
 *
 * <p>Separate from {@link Employee} so the wire format is a deliberate choice
 * rather than whatever the model happens to hold. Returning the entity directly
 * is how internal fields leak the day someone adds one.
 *
 * @param initials precomputed for the avatar badge, so each of the four product
 *     UIs does not re-derive them from the name with its own edge cases
 * @param exitedOn null unless {@code status} is {@link EmploymentStatus#EXITED}
 */
public record EmployeeResponse(
    String id,
    String fullName,
    String email,
    String branchCode,
    String title,
    EmploymentStatus status,
    LocalDate joinedOn,
    LocalDate exitedOn,
    String initials) {

  public static EmployeeResponse from(Employee employee) {
    return new EmployeeResponse(
        employee.getId(),
        employee.getFullName(),
        employee.getEmail(),
        employee.getBranchCode(),
        employee.getTitle(),
        employee.getStatus(),
        employee.getJoinedOn(),
        employee.getExitedOn(),
        employee.initials());
  }
}
