package com.karaka.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the self-service password reset, bound from {@code karaka.password-reset.*}.
 *
 * <p>This flow exists because Keycloak's own reset page deliberately answers identically
 * whether or not an account exists, and the product decision here is to tell the user
 * plainly when it does not. Keycloak offers no setting for that — its {@code Choose User}
 * authenticator reports {@code configurable=false} — so the lookup happens here instead,
 * against the Admin API.
 *
 * <p>The rate limits are not optional decoration. Revealing whether an account exists turns
 * this endpoint into a username-enumeration oracle, and Keycloak's own
 * {@code bruteForceProtected} does not cover it: that guards login attempts, not a custom
 * endpoint. Without these limits it is an unmetered "is this person an employee?" API.
 *
 * @param adminClientId     confidential client whose service account performs the lookup;
 *                          needs {@code view-users} and {@code manage-users}
 * @param adminClientSecret that client's secret
 * @param emailClientId     client the reset link returns the user to
 * @param maxPerWindow      requests allowed per caller IP, and separately per identifier,
 *                          inside {@code window}
 * @param window            the sliding window those counts apply to
 */
@Validated
@ConfigurationProperties(prefix = "karaka.password-reset")
public record PasswordResetProperties(
    @DefaultValue("karaka-api") @NotBlank String adminClientId,
    @DefaultValue("") String adminClientSecret,
    @DefaultValue("karaka-web") @NotBlank String emailClientId,
    @DefaultValue("5") @Positive int maxPerWindow,
    @DefaultValue("15m") Duration window) {

  /** False when no secret is configured, in which case the endpoint stays disabled. */
  public boolean isConfigured() {
    return adminClientSecret != null && !adminClientSecret.isBlank();
  }
}
