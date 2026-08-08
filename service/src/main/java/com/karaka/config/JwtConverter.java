package com.karaka.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Turns a Keycloak access token into a Spring Security authentication.
 *
 * <p>Keycloak does not put roles anywhere Spring looks by default. It nests them
 * in two different places, and this reads both:
 *
 * <ul>
 *   <li>{@code realm_access.roles} — realm roles, shared across every client in
 *       the realm. Suite-wide entitlements ({@code PRODUCT_AURA}) live here.
 *   <li>{@code resource_access.<client-id>.roles} — client roles, scoped to one
 *       client. Per-product permissions ({@code orbit_manage}) live here.
 * </ul>
 *
 * <p>Reading only one of the two is the usual cause of "my token clearly contains
 * the role but {@code hasRole} returns false". Standard {@code scope} claims are
 * still mapped by the delegate converter, so {@code SCOPE_*} authorities keep
 * working alongside {@code ROLE_*}.
 *
 * <p>Every role is prefixed {@code ROLE_} because that is what
 * {@code hasRole('x')} looks for — it prepends the prefix before comparing. Use
 * {@code hasAuthority('ROLE_x')} if you ever need to bypass that.
 */
@Component
public class JwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String REALM_ACCESS = "realm_access";
  private static final String RESOURCE_ACCESS = "resource_access";
  private static final String ROLES = "roles";
  private static final String ROLE_PREFIX = "ROLE_";

  private final JwtGrantedAuthoritiesConverter scopeConverter =
      new JwtGrantedAuthoritiesConverter();

  private final String resourceId;
  private final String principalAttribute;

  JwtConverter(
      @Value("${karaka.jwt.resource-id}") String resourceId,
      @Value("${karaka.jwt.principal-attribute:preferred_username}") String principalAttribute) {
    this.resourceId = resourceId;
    this.principalAttribute = principalAttribute;
  }

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    Collection<GrantedAuthority> fromScopes = scopeConverter.convert(jwt);
    if (fromScopes != null) {
      authorities.addAll(fromScopes);
    }
    authorities.addAll(realmRoles(jwt));
    authorities.addAll(clientRoles(jwt));
    return new JwtAuthenticationToken(jwt, authorities, principalName(jwt));
  }

  /**
   * The name shown in logs and returned by {@code getName()}. Falls back to
   * {@code sub} — a UUID — when the configured attribute is absent, because a
   * null principal name makes {@link JwtAuthenticationToken} throw and turns a
   * missing claim into a 500 rather than a usable identity.
   */
  private String principalName(Jwt jwt) {
    String configured = jwt.getClaimAsString(principalAttribute);
    return StringUtils.hasText(configured) ? configured : jwt.getClaimAsString(JwtClaimNames.SUB);
  }

  private Collection<GrantedAuthority> realmRoles(Jwt jwt) {
    return rolesFrom(jwt.getClaimAsMap(REALM_ACCESS));
  }

  private Collection<GrantedAuthority> clientRoles(Jwt jwt) {
    Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
    if (resourceAccess == null || !(resourceAccess.get(resourceId) instanceof Map<?, ?> resource)) {
      return List.of();
    }
    return rolesFrom(resource);
  }

  /**
   * Pattern matching rather than a cast: a token is attacker-influenced input, so
   * a claim of an unexpected shape must degrade to "no roles" instead of throwing
   * a {@link ClassCastException} out of the filter chain.
   */
  private Collection<GrantedAuthority> rolesFrom(Map<?, ?> claim) {
    if (claim == null || !(claim.get(ROLES) instanceof Collection<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .filter(role -> role instanceof String text && !text.isBlank())
        .map(String::valueOf)
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
        .toList();
  }
}
