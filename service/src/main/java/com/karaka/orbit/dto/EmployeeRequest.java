package com.karaka.orbit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Incoming employee details, for both create and update.
 *
 * <p>Note what is <em>absent</em>: no {@code id} and no {@code status}. Accepting
 * an id would let a caller overwrite an arbitrary record, and accepting a status
 * would let them exit an employee through the edit form, bypassing the exit-date
 * rules. Both are server-controlled and have their own endpoints.
 *
 * <p>Messages are written for a person reading them under a form field, since
 * {@code GlobalExceptionHandler} passes them straight through to the UI.
 */
public record EmployeeRequest(
    @NotBlank(message = "Enter the employee's full name")
        @Size(max = 120, message = "Name must be 120 characters or fewer")
        String fullName,

    @NotBlank(message = "Enter a work email")
        @Email(message = "That does not look like a valid email address")
        @Size(max = 254, message = "Email must be 254 characters or fewer")
        String email,

    @NotBlank(message = "Choose a branch")
        @Pattern(regexp = "^[A-Za-z]{2,5}$", message = "Branch code must be 2-5 letters")
        String branchCode,

    @NotBlank(message = "Enter a job title")
        @Size(max = 80, message = "Title must be 80 characters or fewer")
        String title,

    // Optional: the service defaults it to today. Future joining dates are
    // rejected because the register records what happened, not what is planned.
    @PastOrPresent(message = "Joining date cannot be in the future") LocalDate joinedOn) {}
