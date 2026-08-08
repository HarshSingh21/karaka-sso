package com.karaka.identity;

import com.karaka.config.TenantProperties;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/**
 * Tells the browser who it is signed in as.
 *
 * <p>The one endpoint every product UI calls on load. A 401 here is how a UI
 * discovers its session expired, which is why the BFF chain answers
 * {@code /api/**} with a status code instead of a login redirect — a redirect
 * would arrive at {@code fetch} as an opaque HTML body.
 */
@RestController
@RequestMapping("/api/session")
class SessionController {

  private static final String PRODUCT_ROLE_PREFIX = "ROLE_PRODUCT_";
  private static final String ROLE_PREFIX = "ROLE_";

  private final TenantProperties tenant;

  SessionController(TenantProperties tenant) {
    this.tenant = tenant;
  }

  @GetMapping
  SessionResponse current(Authentication authentication) {
    String username = AuthenticatedActor.usernameOf(authentication);
    String displayName = displayNameOf(authentication, username);

    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .sorted()
        .toList();

    // PRODUCT_ORBIT -> ORBIT. Entitlements are a projection of roles rather than
    // a second list to keep in sync; adding PRODUCT_BURA in Keycloak is all it
    // takes for the BURA tile to light up.
    List<String> entitlements = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(PRODUCT_ROLE_PREFIX))
        .map(authority -> authority.substring(PRODUCT_ROLE_PREFIX.length()))
        .sorted()
        .toList();

    return new SessionResponse(
        username,
        displayName,
        emailOf(authentication),
        initialsOf(displayName),
        tenant.name(),
        roles,
        entitlements);
  }

  private String displayNameOf(Authentication authentication, String fallback) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof OidcUser oidcUser && StringUtils.hasText(oidcUser.getFullName())) {
      return oidcUser.getFullName();
    }
    if (principal instanceof Jwt jwt) {
      String name = jwt.getClaimAsString(StandardClaimNames.NAME);
      if (StringUtils.hasText(name)) {
        return name;
      }
    }
    return fallback;
  }

  private String emailOf(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof OidcUser oidcUser) {
      return oidcUser.getEmail();
    }
    if (principal instanceof Jwt jwt) {
      return jwt.getClaimAsString(StandardClaimNames.EMAIL);
    }
    return null;
  }

  /** Up to two initials for the rail's identity row. */
  private String initialsOf(String name) {
    StringBuilder initials = new StringBuilder(2);
    for (String part : name.split("[\\s@._-]+")) {
      if (!part.isEmpty() && initials.length() < 2) {
        initials.append(Character.toUpperCase(part.charAt(0)));
      }
    }
    return initials.isEmpty() ? "?" : initials.toString();
  }
}
