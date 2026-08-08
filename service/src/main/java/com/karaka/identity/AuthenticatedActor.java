package com.karaka.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Resolves a human-readable username from either authentication type.
 *
 * <p>Needed because the two security chains produce different principals: the BFF
 * chain yields an {@link OidcUser}, the machine chain a {@link Jwt}. Worse,
 * {@code Authentication#getName()} on an {@code OidcUser} returns the {@code sub}
 * claim — a UUID — which would make every audit line unreadable.
 *
 * <p>Preference order is {@code preferred_username}, then {@code email}, then
 * whatever {@code getName()} gives. The final fallback is deliberate: an audit
 * entry attributed to a UUID is still better than one attributed to nobody.
 */
public final class AuthenticatedActor {

  private static final String PREFERRED_USERNAME = "preferred_username";
  private static final String ANONYMOUS = "anonymous";

  private AuthenticatedActor() {}

  public static String usernameOf(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return ANONYMOUS;
    }
    Object principal = authentication.getPrincipal();

    if (principal instanceof OidcUser oidcUser) {
      return firstNonBlank(
          oidcUser.getPreferredUsername(), oidcUser.getEmail(), authentication.getName());
    }
    if (principal instanceof Jwt jwt) {
      return firstNonBlank(jwt.getClaimAsString(PREFERRED_USERNAME), authentication.getName());
    }
    return firstNonBlank(authentication.getName(), ANONYMOUS);
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (StringUtils.hasText(candidate)) {
        return candidate;
      }
    }
    return ANONYMOUS;
  }
}
