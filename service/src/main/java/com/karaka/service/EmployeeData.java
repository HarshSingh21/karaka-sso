package com.karaka.service;

import java.time.LocalDate;

/**
 * The mutable facts about an employee, as supplied by a caller.
 *
 * <p>A command object rather than five loose parameters: {@code create} and
 * {@code update} both need the same set, and a five-{@code String} signature is
 * exactly the shape where {@code title} and {@code branchCode} get silently
 * swapped at a call site and still compile.
 *
 * <p>Lives in the service package, not {@code web.dto}, so the service layer does
 * not depend on the HTTP layer. Controllers map their request records into this;
 * a scheduled import or a CSV loader would build it directly.
 */
public record EmployeeData(
    String fullName, String email, String branchCode, String title, LocalDate joinedOn) {}
