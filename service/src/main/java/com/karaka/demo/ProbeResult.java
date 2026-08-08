package com.karaka.demo;

/**
 * Outcome of a successful capability probe.
 *
 * @param role the realm role that permitted the call
 * @param allowed always {@code true} — a denial never reaches the controller, it
 *     comes back as a 403 ProblemDetail. The field exists so the UI can read one
 *     consistent shape instead of inferring success from the absence of an error.
 * @param message plain-language explanation, shown directly in the demo UI
 */
public record ProbeResult(String role, boolean allowed, String message) {}
