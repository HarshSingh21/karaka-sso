package com.karaka.demo;

import java.util.List;

/**
 * The suite catalogue: one entry per product, with its capabilities.
 *
 * <p>The single place product metadata lives. The picker and the per-product demo
 * page both render from this over HTTP rather than hard-coding their own copy —
 * four hard-coded lists in four HTML files is exactly how a fifth product ends up
 * missing from one of them.
 *
 * <p>Capability names are assembled as {@code <PRODUCT>_<CAPABILITY>} and must
 * match the realm roles in {@code keycloak/realm/karaka-realm.json}. A mismatch
 * here does not fail the build — it silently shows a capability nobody can ever
 * hold — so the names are asserted by {@code SuiteProductTest}.
 */
public enum SuiteProduct {
  ORBIT(
      "Employee register",
      "Directory, branches, and the audit trail.",
      List.of(
          new Capability("VIEW", "Read the employee register"),
          new Capability("MANAGE", "Create, edit, transfer and exit employees"),
          new Capability("AUDIT", "Read the audit trail of who changed what"))),

  AURA(
      "Accounting",
      "Ledgers, journals, and reconciliation.",
      List.of(
          new Capability("VIEW", "Read ledgers, journals and balances"),
          new Capability("POST", "Post and reverse journal entries"),
          new Capability("CLOSE", "Reconcile accounts and close a period"))),

  LURA(
      "Location",
      "Sites, geofences, and field movement.",
      List.of(
          new Capability("VIEW", "Read sites, geofences and visit history"),
          new Capability("MANAGE", "Create and edit sites and geofences"),
          new Capability("TRACK", "See live location of named people"))),

  BURA(
      "Biometric attendance",
      "Devices, punches, and shift records.",
      List.of(
          new Capability("VIEW", "Read devices, punches and shift records"),
          new Capability("MANAGE", "Enrol devices and assign shifts"),
          new Capability("ADJUST", "Correct or void a biometric punch")));

  /** One permission within a product. {@code name} is the suffix, not the full role. */
  public record Capability(String name, String description) {}

  private final String title;
  private final String description;
  private final List<Capability> capabilities;

  SuiteProduct(String title, String description, List<Capability> capabilities) {
    this.title = title;
    this.description = description;
    this.capabilities = List.copyOf(capabilities);
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public List<Capability> capabilities() {
    return capabilities;
  }

  /** The realm role granting entry to this product, e.g. {@code PRODUCT_AURA}. */
  public String entitlementRole() {
    return "PRODUCT_" + name();
  }

  /** The realm role for one capability, e.g. {@code AURA_POST}. */
  public String roleFor(Capability capability) {
    return name() + "_" + capability.name();
  }

  /** Where the product's UI lives. Lower-case name, so a new enum constant is enough. */
  public String path() {
    return "/" + name().toLowerCase(java.util.Locale.ROOT);
  }
}
