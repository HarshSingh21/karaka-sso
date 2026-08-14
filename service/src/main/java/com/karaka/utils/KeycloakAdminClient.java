package com.karaka.utils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.karaka.config.PasswordResetProperties;
import com.karaka.exception.PasswordResetUnavailableException;
import lombok.extern.slf4j.Slf4j;

/**
 * The slice of Keycloak's Admin API this application needs: find a user, and ask Keycloak to
 * email them a password-reset link.
 *
 * <p>Deliberately the Admin API and <em>not</em> Keycloak's Postgres database. Keycloak owns
 * that schema and changes it between versions — session storage moved out of
 * {@code user_session} in 26.x, and a direct query would have kept working right up to an
 * upgrade and then silently returned nothing. The Admin API is the supported contract and
 * applies Keycloak's own authorization on top.
 *
 * <p>Keycloak sends the mail, so no email template or SMTP credential lives in this
 * application. The reset link is a Keycloak action token, which means the password itself is
 * still only ever set through Keycloak.
 */
@Component
@Slf4j
public class KeycloakAdminClient {

  /** Refresh a little before expiry so a request never travels with a just-expired token. */
  private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

  private static final ParameterizedTypeReference<Map<String, Object>> MAP =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
      new ParameterizedTypeReference<>() {};

  private final RestClient http;
  private final PasswordResetProperties config;
  private final Clock clock;
  private final String realm;
  private final String serverBase;

  /** Guarded by {@code this}; the token is shared and reused across requests. */
  private String cachedToken;

  private Instant cachedTokenExpiry = Instant.EPOCH;

  public KeycloakAdminClient(
      RestClient.Builder builder,
      PasswordResetProperties config,
      Clock clock,
      // The same issuer the resource server and BFF already validate against. Reusing it means
      // the admin base URL cannot drift from the one tokens are checked against.
      @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}") String issuerUri) {
    this.http = builder.build();
    this.config = config;
    this.clock = clock;
    // issuer-uri is the one URL already configured for Keycloak, so the admin base and realm
    // are derived from it rather than introduced as two more settings that can disagree.
    int marker = issuerUri.lastIndexOf("/realms/");
    if (marker < 0) {
      throw new IllegalArgumentException(
          "issuer-uri must contain /realms/<realm>, got: " + issuerUri);
    }
    this.serverBase = issuerUri.substring(0, marker);
    this.realm = issuerUri.substring(marker + "/realms/".length()).replaceAll("/+$", "");
  }

  /**
   * Looks a user up by username, then by email.
   *
   * <p>Two exact lookups rather than one fuzzy {@code search=}: fuzzy matching would return
   * partial hits, and "did you mean" behaviour on a password reset is how one user is sent a
   * link for another user's account.
   */
  public Optional<KeycloakUser> findByUsernameOrEmail(String identifier) {
    return findExact("username", identifier).or(() -> findExact("email", identifier));
  }

  private Optional<KeycloakUser> findExact(String field, String value) {
    List<Map<String, Object>> hits =
        http.get()
            .uri(
                serverBase + "/admin/realms/{realm}/users?{field}={value}&exact=true&max=2",
                realm,
                field,
                value)
            .header("Authorization", "Bearer " + serviceAccountToken())
            .retrieve()
            .body(LIST_OF_MAP);

    if (hits == null || hits.isEmpty()) {
      return Optional.empty();
    }
    if (hits.size() > 1) {
      // Only reachable with duplicateEmailsAllowed, which this realm disables. Refusing is
      // the safe branch: sending to an arbitrary one of them is worse than declining.
      log.warn("{} '{}' matched {} users — refusing to guess", field, value, hits.size());
      return Optional.empty();
    }
    Map<String, Object> user = hits.getFirst();
    return Optional.of(
        new KeycloakUser(
            String.valueOf(user.get("id")),
            asString(user.get("username")),
            asString(user.get("email")),
            Boolean.TRUE.equals(user.get("enabled"))));
  }

  /**
   * Asks Keycloak to email an {@code UPDATE_PASSWORD} action link.
   *
   * <p>{@code redirectUri} must match a registered redirect for {@code emailClientId} or
   * Keycloak rejects the call, so a misconfigured realm fails here rather than sending a link
   * that dead-ends after the password is changed.
   */
  public void sendPasswordResetEmail(String userId, String redirectUri) {
    http.put()
        .uri(
            serverBase
                + "/admin/realms/{realm}/users/{id}/execute-actions-email"
                + "?client_id={clientId}&redirect_uri={redirectUri}",
            realm,
            userId,
            config.emailClientId(),
            redirectUri)
        .header("Authorization", "Bearer " + serviceAccountToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(List.of("UPDATE_PASSWORD"))
        .retrieve()
        .toBodilessEntity();
  }

  /** Client-credentials token for the service account, cached until shortly before expiry. */
  private synchronized String serviceAccountToken() {
    Instant now = clock.instant();
    if (cachedToken != null && now.isBefore(cachedTokenExpiry)) {
      return cachedToken;
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", config.adminClientId());
    form.add("client_secret", config.adminClientSecret());

    Map<String, Object> token;
    try {
      token =
          http.post()
              .uri(serverBase + "/realms/{realm}/protocol/openid-connect/token", realm)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(MAP);
    } catch (RestClientException e) {
      throw new PasswordResetUnavailableException(
          "could not obtain a service-account token for " + config.adminClientId(), e);
    }
    if (token == null || token.get("access_token") == null) {
      throw new PasswordResetUnavailableException("token response contained no access_token");
    }
    cachedToken = String.valueOf(token.get("access_token"));
    long ttl = token.get("expires_in") instanceof Number n ? n.longValue() : 60L;
    cachedTokenExpiry = now.plusSeconds(ttl).minus(EXPIRY_MARGIN);
    return cachedToken;
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /** The fields of a Keycloak user this flow needs. */
  public record KeycloakUser(String id, String username, String email, boolean enabled) {

    public boolean canReceiveEmail() {
      return email != null && !email.isBlank();
    }
  }
}
