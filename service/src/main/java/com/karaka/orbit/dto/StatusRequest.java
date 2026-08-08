package com.karaka.orbit.dto;

import com.karaka.orbit.model.EmploymentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body for moving an employee between ACTIVE and INACTIVE.
 *
 * <p>{@link EmploymentStatus#EXITED} is rejected by the model, not here: exiting
 * needs a date, so it has its own endpoint. Letting it through would produce a
 * record marked exited with no leaving date.
 */
public record StatusRequest(@NotNull(message = "Choose a status") EmploymentStatus status) {}
