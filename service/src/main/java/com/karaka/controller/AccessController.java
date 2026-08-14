package com.karaka.controller;

import com.karaka.config.TenantProperties;
import com.karaka.dto.CatalogueResponse.CapabilityAccess;
import com.karaka.dto.CatalogueResponse.ProductAccess;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.karaka.dto.ProbeResult;
import com.karaka.exception.GlobalExceptionHandler;
import com.karaka.model.enums.SuiteProduct;
import com.karaka.dto.CatalogueResponse;
import lombok.RequiredArgsConstructor;

/**
 * What the signed-in user may do, and a way to prove it.
 *
 * <p>Two endpoints with deliberately different trust levels:
 *
 * <ul>
 *   <li>{@link #catalogue} <em>reports</em> access. It reads the authorities Spring
 *       derived from the Keycloak token, so the UI can grey out an action instead of
 *       offering a button that will fail.
 *   <li>{@link #probe} <em>demonstrates</em> access. It is guarded by
 *       {@code @PreAuthorize}, so a caller lacking the capability never reaches the
 *       method body and receives a 403 from Spring Security itself.
 * </ul>
 *
 * <p>That split is the point. A screen that only ever asks the server "what am I
 * allowed to do?" proves nothing — the server could be lying, or the UI could be
 * misreading. The probe endpoint produces a real allow or a real deny from the
 * authorization layer, which is what makes this a demonstration rather than a claim.
 */
@RestController
@RequestMapping("/api/access")
@Validated
@RequiredArgsConstructor
public class AccessController {

  private static final String ROLE_PREFIX = "ROLE_";

  private final TenantProperties tenant;


  /** Every product with this caller's entitlement and capability flags. */
  @GetMapping("/catalogue")
  CatalogueResponse catalogue(Authentication authentication) {
    Set<String> held = heldRoles(authentication);

    List<ProductAccess> products =
        java.util.Arrays.stream(SuiteProduct.values())
            .map(product -> toProductAccess(product, held))
            .toList();

    return new CatalogueResponse(tenant.name(), products);
  }

  /**
   * Attempts a capability and reports what the authorization layer decided.
   *
   * <p>The role name is assembled in SpEL from the path, so one method covers every
   * product and capability instead of twelve near-identical ones. Both path segments
   * are constrained by {@link Pattern} first: without that, a caller could aim the
   * expression at any role name in the realm. The check would still be honest — it
   * would just be answering a question about a role that has nothing to do with this
   * suite, which makes the endpoint a role-enumeration oracle.
   *
   * <p>Reaching the body at all means the check passed, so this can only ever return
   * {@code allowed=true}. A denial surfaces as the 403 produced by Spring Security
   * and rendered by {@code GlobalExceptionHandler} — that response, not this one, is
   * the interesting half of the demo.
   */
  @PostMapping("/probe/{product}/{capability}")
  @PreAuthorize("hasRole(#product.toUpperCase() + '_' + #capability.toUpperCase())")
  ProbeResult probe(
      @PathVariable @Pattern(regexp = "(?i)orbit|aura|lura|bura", message = "unknown product")
          String product,
      @PathVariable @Pattern(regexp = "(?i)view|manage|audit|post|close|track|adjust",
              message = "unknown capability")
          String capability,
      Authentication authentication) {

    String role =
        product.toUpperCase(java.util.Locale.ROOT)
            + "_"
            + capability.toUpperCase(java.util.Locale.ROOT);

    return new ProbeResult(
        role,
        true,
        authentication.getName() + " holds " + role + ", so this action was allowed.");
  }

  private ProductAccess toProductAccess(SuiteProduct product, Set<String> held) {
    List<CapabilityAccess> capabilities =
        product.capabilities().stream()
            .map(capability -> {
              String role = product.roleFor(capability);
              return new CapabilityAccess(
                  capability.name(), role, capability.description(), held.contains(role));
            })
            .toList();

    return new ProductAccess(
        product.name(),
        product.title(),
        product.description(),
        product.path(),
        held.contains(product.entitlementRole()),
        capabilities);
  }

  /** Granted authorities as bare realm-role names, with the {@code ROLE_} prefix dropped. */
  private Set<String> heldRoles(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .collect(Collectors.toUnmodifiableSet());
  }
}
