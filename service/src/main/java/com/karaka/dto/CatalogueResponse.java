package com.karaka.dto;

import java.util.List;

/**
 * The suite catalogue as the UI needs it: every product, and for this caller,
 * whether they may enter it and which capabilities they hold.
 *
 * <p>Locked products are still listed. Hiding a product a user cannot enter would
 * leave them unable to tell that it exists and that access is something they could
 * request — the same reason the design system keeps a {@code SOON} tile rather than
 * omitting one.
 */
public record CatalogueResponse(String tenant, List<ProductAccess> products) {

  /**
   * @param entitled whether the caller holds {@code PRODUCT_<CODE>}
   * @param capabilities every capability the product defines, each flagged with
   *     whether this caller holds it — so the UI can show what is withheld, not
   *     just what is granted
   */
  public record ProductAccess(
      String code,
      String title,
      String description,
      String path,
      boolean entitled,
      List<CapabilityAccess> capabilities) {}

  /**
   * @param role the full realm role name, shown in the UI so the demo makes the
   *     link between a Keycloak role and an allowed action explicit
   */
  public record CapabilityAccess(
      String name, String role, String description, boolean granted) {}
}
