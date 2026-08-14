package com.karaka.config.secure;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * The BFF-side counterpart to {@link JwtConverter}.
 *
 * <p>Both extract Keycloak roles, but from different places, which is why there
 * are two classes rather than one. A resource server authenticates from an
 * <em>access token</em>; {@code oauth2Login} authenticates from an <em>ID
 * token</em> plus userinfo. Keycloak's built-in mapper writes realm roles to the
 * access token only, so the BFF sees no roles at all unless
 * {@code karaka-realm.json} adds a realm-role mapper with
 * {@code id.token.claim=true}. Deleting that mapper silently strips every role
 * from the browser session while leaving the Postman path working — a confusing
 * failure worth naming here.
 */
public final class KeycloakRealmRoleMapper implements GrantedAuthoritiesMapper {

  private static final String REALM_ACCESS = "realm_access";
  private static final String ROLES = "roles";
  private static final String ROLE_PREFIX = "ROLE_";

  @Override
  public Collection<? extends GrantedAuthority> mapAuthorities(
      Collection<? extends GrantedAuthority> authorities) {

    // LinkedHashSet: de-duplicates a role present in both the ID token and
    // userinfo, while keeping order stable for logs and tests.
    Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);

    for (GrantedAuthority authority : authorities) {
      if (authority instanceof OidcUserAuthority oidcAuthority) {
        mapped.addAll(realmRoles(oidcAuthority.getIdToken().getClaims()));
        if (oidcAuthority.getUserInfo() != null) {
          mapped.addAll(realmRoles(oidcAuthority.getUserInfo().getClaims()));
        }
      }
    }
    return mapped;
  }

  private Collection<GrantedAuthority> realmRoles(Map<String, Object> claims) {
    if (!(claims.get(REALM_ACCESS) instanceof Map<?, ?> realmAccess)) {
      return List.of();
    }
    if (!(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .filter(role -> !role.isBlank())
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
        .toList();
  }
}
