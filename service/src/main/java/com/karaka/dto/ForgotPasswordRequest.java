package com.karaka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A reset request.
 *
 * <p>The upper bound is not cosmetic: without it an attacker can post megabyte identifiers to
 * make the lookup expensive. 320 is the longest legal email address (64 local + @ + 255 domain).
 */
public record ForgotPasswordRequest(@NotBlank @Size(max = 320) String identifier) {}
