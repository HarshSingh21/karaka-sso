package com.karaka.config;

import com.karaka.demo.SuiteProduct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Two filter chains, split by <em>who is calling</em> rather than by URL.
 *
 * <table>
 *   <caption>Chain selection</caption>
 *   <tr><th>Caller</th><th>Chain</th><th>Credential</th></tr>
 *   <tr><td>Biometric terminal, mobile app, payroll integration, Postman</td>
 *       <td>{@link #machineApiChain} — stateless</td>
 *       <td>{@code Authorization: Bearer <jwt>}</td></tr>
 *   <tr><td>ORBIT / AURA / LURA / BURA browser UIs</td>
 *       <td>{@link #browserChain} — BFF</td>
 *       <td>Keycloak SSO session, exchanged for a {@code KARAKA_SESSION} cookie</td></tr>
 * </table>
 *
 * <p><b>Why both.</b> A browser must not hold an access token: it is valid across
 * the whole suite, so one XSS in the newest product would yield a credential that
 * also opens the others. A device has no cookie jar and cannot use a session.
 * Neither pattern serves both callers, so there are two chains.
 *
 * <p><b>Why the order matters.</b> {@link #machineApiChain} is {@code @Order(1)}
 * and matches only requests that actually carry a bearer token. Everything else —
 * including an unauthenticated browser hitting {@code /api/...} — falls through to
 * the BFF chain, which knows how to answer with a 401 or a redirect. Reverse the
 * order and every API call would be handed a login redirect instead.
 *
 * <p><b>Three layers of authorization</b>, deliberately, because any one of them
 * alone leaves a hole:
 *
 * <ol>
 *   <li><b>URL entitlement</b> here — {@code /aura} requires {@code PRODUCT_AURA},
 *       so an un-entitled user cannot even load the page.
 *   <li><b>Method capability</b> via {@code @PreAuthorize} on controllers — the
 *       action-level check, and the one that produces the 403s the demo shows.
 *   <li><b>UI affordance</b> in the pages — a withheld control is not rendered.
 *       Presentation only; it protects nothing on its own.
 * </ol>
 *
 * <p><b>Growth path.</b> When products become separate deployables these split
 * cleanly: {@link #browserChain} becomes the gateway's configuration and
 * {@link #machineApiChain} becomes each product service's.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

  /** Where a failed sign-in lands. Must be public — the user has no session yet. */
  private static final String SIGN_IN_FAILED = "/sign-in-failed";

  private static final String KEYCLOAK_AUTHORIZATION_URI =
      OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/keycloak";

  private static final String[] PUBLIC_PATHS = {
    "/shared/**", "/favicon.svg", "/error", SIGN_IN_FAILED, SIGN_IN_FAILED + "/**",
    "/actuator/health", "/actuator/health/**"
  };

  /**
   * Stateless chain for machine callers.
   *
   * <p>The matcher is the interesting part: {@code /api/**} <em>and</em> a
   * {@code Bearer} Authorization header. Matching on the path alone would capture
   * the browser's own {@code fetch} calls, which authenticate by cookie and would
   * then be rejected for carrying no token.
   */
  @Bean
  @Order(1)
  SecurityFilterChain machineApiChain(HttpSecurity http, JwtConverter jwtConverter)
      throws Exception {
    return http
        .securityMatcher(bearerTokenOnApi())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 ->
            oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
        // No session is created, so there is no cookie for another site to ride on
        // — which is precisely why disabling CSRF is safe here. Doing the same on
        // the cookie-based chain below would not be.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // A machine client gets a status code, never a redirect to a login page.
        .exceptionHandling(ex ->
            ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }

  /** Session-based chain for browsers: the backend-for-frontend. */
  @Bean
  @Order(2)
  SecurityFilterChain browserChain(
      HttpSecurity http,
      ClientRegistrationRepository clientRegistrations,
      GrantedAuthoritiesMapper authoritiesMapper)
      throws Exception {

    return http
        .authorizeHttpRequests(auth -> {
          auth.requestMatchers(PUBLIC_PATHS).permitAll();

          // Entitlement enforced at the URL, not only in the UI. Hiding a tile a
          // user cannot enter is presentation; this is the control. Driven by the
          // SuiteProduct enum, so a new product cannot be added and left unguarded.
          for (SuiteProduct product : SuiteProduct.values()) {
            auth.requestMatchers(pathsOf(product)).hasRole(product.entitlementRole());
          }

          // The shared shell behind those four routes, reached by an internal
          // forward from /orbit, /aura, /lura, /bura.
          //
          // It must be `authenticated()`, NOT `denyAll()`. Spring Security 6's
          // AuthorizationFilter authorizes every dispatcher type including FORWARD,
          // so denying this path also denies the forward and every product page
          // returns 403 — for entitled users too. Verified the hard way.
          //
          // Serving it to any signed-in user is fine: the file is an empty shell.
          // It holds no data and renders nothing until /api/access/catalogue and
          // /api/session answer, and both of those are authorized on their own.
          auth.requestMatchers("/product/**").authenticated();

          // Everything else — the picker included — just needs a session, so
          // hitting / bounces straight to the Karaka login theme.
          auth.anyRequest().authenticated();
        })

        .oauth2Login(oauth -> oauth
            // Keycloak IS the login page. Without this, Spring auto-generates one at
            // /login listing the provider ("Login with OAuth 2.0"), and on failure
            // renders /login?error — a dead end reading "Invalid credentials" with
            // no way forward, even when the password was correct and the real cause
            // was a stale authorization code.
            .loginPage(KEYCLOAK_AUTHORIZATION_URI)
            .authorizationEndpoint(endpoint ->
                endpoint.authorizationRequestResolver(pkceResolver(clientRegistrations)))
            .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(authoritiesMapper))
            // A failed exchange is usually recoverable — a code reused, a session
            // dropped by a restart, cookies from a realm that no longer exists. Send
            // the user somewhere that says so and offers a clean retry.
            .failureHandler(new SimpleUrlAuthenticationFailureHandler(SIGN_IN_FAILED))
            // false: honour the saved request, so a deep link survives login.
            .defaultSuccessUrl("/picker", false))

        .logout(logout -> logout
            // RP-initiated logout ends the Keycloak SSO session too. Dropping only
            // the local cookie would leave the shared session alive and the next
            // visit would sign the same user straight back in — the sharp edge of
            // SSO if you forget this line.
            .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrations))
            .invalidateHttpSession(true)
            .deleteCookies("KARAKA_SESSION"))

        .csrf(csrf -> csrf
            // Readable by JS so pages can echo it back in a header or a hidden form
            // field. The value is not a secret; its unpredictability is what matters.
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CookieCsrfTokenRequestHandler()))

        // A browser navigation should redirect to Keycloak, but a fetch to /api/**
        // wants a 401 it can act on rather than a login page it would try to parse
        // as JSON.
        //
        // BOTH mappings are required. Spring only builds a delegating entry point
        // when more than one is registered; with a single mapping it quietly uses
        // that one for every request, so registering only the /api/** 401 makes
        // GET /picker answer 401 too and the login redirect never happens.
        .exceptionHandling(ex -> ex
            .defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
            .defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint(KEYCLOAK_AUTHORIZATION_URI),
                AnyRequestMatcher.INSTANCE)
            // Sends a refused browser navigation to a page that explains itself,
            // instead of Boot's Whitelabel "unexpected error" for a denial that is
            // entirely expected.
            .accessDeniedHandler(new BrowserAccessDeniedHandler()))

        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation(fixation -> fixation.changeSessionId()))

        .headers(headers -> headers
            .frameOptions(frame -> frame.deny())
            .contentTypeOptions(Customizer.withDefaults()))

        .build();
  }

  /**
   * A product's page and everything beneath it.
   *
   * <p>Both forms are needed: {@code /aura} is the route itself, and {@code /aura/**}
   * covers the trailing-slash redirect and any per-product asset added later.
   */
  private static String[] pathsOf(SuiteProduct product) {
    return new String[] {product.path(), product.path() + "/**"};
  }

  /**
   * Sends {@code code_challenge}/{@code code_verifier} on the authorization-code flow.
   *
   * <p>Required, not optional hardening: the {@code karaka-web} client in
   * {@code karaka-realm.json} sets {@code pkce.code.challenge.method=S256}, so
   * Keycloak rejects any authorization request without a challenge —
   * {@code invalid_request: Missing parameter: code_challenge_method}. Spring adds
   * PKCE automatically only for <em>public</em> clients; a confidential client like
   * this one has to opt in here.
   *
   * <p>Worth keeping even though the client secret already protects the token
   * exchange: PKCE binds the code to this specific login attempt, so a code
   * intercepted from the redirect cannot be replayed. OAuth 2.1 makes it mandatory
   * for every client type.
   */
  private OAuth2AuthorizationRequestResolver pkceResolver(
      ClientRegistrationRepository clientRegistrations) {
    var resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrations,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    return resolver;
  }

  /** {@code /api/**} carrying a {@code Bearer} token — see {@link #machineApiChain}. */
  private RequestMatcher bearerTokenOnApi() {
    RequestMatcher onApi = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    RequestMatcher hasBearer =
        request -> {
          String header = request.getHeader(HttpHeaders.AUTHORIZATION);
          return StringUtils.hasText(header) && header.startsWith("Bearer ");
        };
    return new AndRequestMatcher(onApi, hasBearer);
  }

  private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
      ClientRegistrationRepository clientRegistrations) {
    var handler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
    // Resolved per request, so this stays correct behind a proxy or on another port
    // without a second config value. Keycloak must list a matching
    // post.logout.redirect.uris entry or it refuses the redirect.
    handler.setPostLogoutRedirectUri("{baseUrl}/");
    return handler;
  }

  @Bean
  GrantedAuthoritiesMapper userAuthoritiesMapper() {
    return new KeycloakRealmRoleMapper();
  }
}
