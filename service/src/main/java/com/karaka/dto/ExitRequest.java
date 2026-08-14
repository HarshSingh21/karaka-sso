package com.karaka.dto;

import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * Body for exiting an employee.
 *
 * <p>A record with one optional field rather than a bare query parameter, so that
 * a reason or a notice period can be added later without changing the endpoint's
 * shape. Null {@code exitOn} means today.
 */
public record ExitRequest(
    @PastOrPresent(message = "Exit date cannot be in the future") LocalDate exitOn) {}
