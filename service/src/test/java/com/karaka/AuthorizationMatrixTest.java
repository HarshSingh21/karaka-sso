package com.karaka;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The authorization matrix, as a test rather than a curl script.
 *
 * <p>This exists because three separate bugs in this project reached a clean,
 * warning-free build and were only caught by running requests by hand: a CSRF handler
 * that accepted a header token but rejected the identical value in a form field, an
 * entry-point mapping that turned every page into a 401, and an employee listing that
 * answered 200 where its siblings answered 403. A compiler cannot see any of those.
 *
 * <p>{@code @WithMockUser(roles = ...)} supplies authorities directly, so no Keycloak is
 * involved. That is the point: these assertions are about what the application does with
 * a set of roles, which is exactly the layer that kept breaking. How the roles arrive is
 * covered separately.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestOAuth2Config.class)
class AuthorizationMatrixTest {

  @Autowired private MockMvc mvc;

  // --- unauthenticated -----------------------------------------------------

  @Test
  @DisplayName("an API call without credentials is 401, not a redirect")
  void apiWithoutCredentialsIsUnauthorized() throws Exception {
    // A fetch caller needs a status code to branch on. A redirect would arrive as an
    // HTML body that fails to parse as JSON — the bug this mapping exists to prevent.
    mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("a page without credentials redirects to Keycloak, not 401")
  void pageWithoutCredentialsRedirects() throws Exception {
    mvc.perform(get("/picker"))
        .andExpect(status().is3xxRedirection());
  }

  // --- read capability -----------------------------------------------------

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "PRODUCT_ORBIT"})
  void viewerCanListEmployees() throws Exception {
    mvc.perform(get("/api/employees"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").exists());
  }

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "PRODUCT_ORBIT"})
  void viewerCannotReadTheAuditTrail() throws Exception {
    // ORBIT_AUDIT is deliberately separate from ORBIT_VIEW: who changed what is a
    // different sensitivity from the directory itself.
    mvc.perform(get("/api/audit")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "PRODUCT_ORBIT"})
  void viewerCannotCreate() throws Exception {
    mvc.perform(post("/api/employees").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"New Person","email":"new@opal.example",
                 "branchCode":"BLR","title":"Analyst"}"""))
        .andExpect(status().isForbidden());
  }

  // --- write capability ----------------------------------------------------

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "ORBIT_MANAGE", "PRODUCT_ORBIT"})
  void managerCanCreate() throws Exception {
    mvc.perform(post("/api/employees").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Created By Test","email":"created-by-test@opal.example",
                 "branchCode":"BLR","title":"Analyst"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.initials").value("CB"));
  }

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "ORBIT_MANAGE", "PRODUCT_ORBIT"})
  @DisplayName("sub-admin writes but still cannot read the audit trail")
  void managerStillCannotReadAudit() throws Exception {
    // The distinction that makes ORBIT_SUBADMIN weaker than ORBIT_ADMIN.
    mvc.perform(get("/api/audit")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "ORBIT_MANAGE", "ORBIT_AUDIT", "PRODUCT_ORBIT"})
  void auditorCanReadTheTrail() throws Exception {
    mvc.perform(get("/api/audit")).andExpect(status().isOk());
  }

  // --- CSRF ----------------------------------------------------------------

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "ORBIT_MANAGE", "PRODUCT_ORBIT"})
  @DisplayName("a mutating request without a CSRF token is refused")
  void mutationWithoutCsrfIsForbidden() throws Exception {
    mvc.perform(post("/api/employees")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"No Csrf","email":"nocsrf@opal.example",
                 "branchCode":"BLR","title":"Analyst"}"""))
        .andExpect(status().isForbidden());
  }

  // --- error contract ------------------------------------------------------

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "ORBIT_MANAGE", "PRODUCT_ORBIT"})
  void validationFailureNamesEveryBadField() throws Exception {
    // The UI renders these under the offending input, so the map shape is part of the
    // contract, not an implementation detail.
    mvc.perform(post("/api/employees").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"","email":"not-an-email","branchCode":"1","title":""}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.fullName").exists())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors.branchCode").exists());
  }

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "ORBIT_MANAGE", "PRODUCT_ORBIT"})
  void duplicateEmailIsDetectedRegardlessOfCase() throws Exception {
    // The seeder holds ankit@opal.example; upper case must still collide.
    mvc.perform(post("/api/employees").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Clone","email":"ANKIT@opal.example",
                 "branchCode":"BLR","title":"Analyst"}"""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors.email").exists());
  }

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "ORBIT_MANAGE", "PRODUCT_ORBIT"})
  void unknownBranchIsUnprocessableNotBadRequest() throws Exception {
    // 422, not 400: the request is well formed but refers to something absent.
    mvc.perform(post("/api/employees").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Ghost","email":"ghost@opal.example",
                 "branchCode":"ZZZ","title":"Analyst"}"""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.branchCode").exists());
  }

  @Test
  @WithMockUser(roles = {"ORBIT_VIEW", "PRODUCT_ORBIT"})
  void unknownEmployeeIsNotFound() throws Exception {
    mvc.perform(get("/api/employees/OPL-9999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.employeeId").value("OPL-9999"));
  }

  // --- session -------------------------------------------------------------

  @Test
  @WithMockUser(username = "tester", roles = {"ORBIT_VIEW", "PRODUCT_ORBIT", "PRODUCT_AURA"})
  void sessionProjectsProductRolesIntoEntitlements() throws Exception {
    // Entitlements are derived from PRODUCT_* rather than maintained as a second list,
    // so this asserts the projection rather than a hard-coded set.
    mvc.perform(get("/api/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entitlements").value(org.hamcrest.Matchers.containsInAnyOrder("ORBIT", "AURA")))
        .andExpect(jsonPath("$.tenant").value("TestTenant"));
  }
}
