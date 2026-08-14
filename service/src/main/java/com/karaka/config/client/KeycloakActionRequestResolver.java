package com.karaka.config.client;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import lombok.RequiredArgsConstructor;

/**
 * Passes Keycloak's {@code kc_action} through to the authorization request.
 *
 * <p>This is what lets Karaka offer "Set up two-factor authentication" or "Change password" as a
 * plain link, with no form, no endpoint and no credential handling of its own: the browser is
 * sent through the normal authorization-code flow, Keycloak performs the requested action on its
 * own themed pages, and the user comes back signed in. Keycloak calls these
 * Application-Initiated Actions.
 *
 * <p>Wrapping the resolver rather than using {@code setAuthorizationRequestCustomizer} is
 * deliberate: the customizer receives only a builder, with no access to the incoming request, so
 * it cannot read the {@code kc_action} query parameter that decides what to ask for.
 *
 * <p>{@link #ALLOWED} is a strict allow-list. Reflecting an arbitrary caller-supplied value into
 * the authorization request would let any link on the internet drive Keycloak actions against a
 * signed-in user — {@code delete_account} being the obvious one to keep out.
 */
@RequiredArgsConstructor
public final class KeycloakActionRequestResolver implements OAuth2AuthorizationRequestResolver {

  /** Query parameter Keycloak reads, and the same name accepted on the way in. */
  private static final String PARAM = "kc_action";

  /**
   * Actions a user may trigger for themselves. Deliberately excludes {@code delete_account} and
   * {@code UPDATE_PROFILE}: the first is destructive, the second is enforced by the realm's user
   * profile and should not be a casual link.
   */
  private static final Set<String> ALLOWED = Set.of("CONFIGURE_TOTP", "UPDATE_PASSWORD");

  private final OAuth2AuthorizationRequestResolver delegate;


  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return withAction(delegate.resolve(request), request);
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
    return withAction(delegate.resolve(request, clientRegistrationId), request);
  }

  private OAuth2AuthorizationRequest withAction(
      OAuth2AuthorizationRequest resolved, HttpServletRequest request) {
    if (resolved == null) {
      // Not an authorization-request URL; nothing to decorate.
      return null;
    }
    String requested = request.getParameter(PARAM);
    if (requested == null || requested.isBlank()) {
      return resolved;
    }
    String action = requested.strip().toUpperCase(Locale.ROOT);
    if (!ALLOWED.contains(action)) {
      // Drop it silently rather than failing the login: an unknown or disallowed action should
      // still let the user sign in, just without the extra step.
      return resolved;
    }
    // additionalParameters, not a custom URI: Spring builds the final URL, so this survives
    // correctly alongside state, nonce and the PKCE challenge.
    return OAuth2AuthorizationRequest.from(resolved)
        .additionalParameters(
            params -> params.putAll(Map.of(PARAM, action)))
        .build();
  }
}
