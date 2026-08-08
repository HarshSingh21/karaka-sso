package com.karaka.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-deployment tenant identity, bound from {@code karaka.tenant.*}.
 *
 * <p>Surfaced in the UI as the quiet {@code .org-tag} pill beside the Karaka
 * wordmark. Karaka is the suite brand and never varies; this does. Keeping it in
 * configuration is what stops a customer's name being compiled into the artifact.
 */
@Validated
@ConfigurationProperties(prefix = "karaka.tenant")
public record TenantProperties(@NotBlank String name) {}
