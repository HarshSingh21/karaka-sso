package com.karaka.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Turns a URL-level authorization failure into a page a person can read.
 *
 * <p>Without this, a denial from {@code authorizeHttpRequests} triggers an ERROR
 * dispatch to {@code /error} and Spring Boot renders its Whitelabel Error Page —
 * "There was an unexpected error (type=Forbidden, status=403)". Which is wrong twice
 * over: it is not unexpected, and it tells the user nothing about what to do next.
 *
 * <p>{@code /api/**} keeps the plain 403. A {@code fetch} caller wants a status code
 * it can branch on, and redirecting it to an HTML page would arrive as an opaque body
 * that fails to parse as JSON. Only browser navigations are redirected.
 *
 * <p>Note this handles the <em>filter</em>-level denial only. An
 * {@code AccessDeniedException} raised by {@code @PreAuthorize} inside a controller
 * propagates to the DispatcherServlet instead and is rendered as an RFC 9457 problem
 * by {@code GlobalExceptionHandler} — two different paths to a 403, deliberately
 * producing two different representations.
 */
final class BrowserAccessDeniedHandler implements AccessDeniedHandler {

  private static final String DENIED_PAGE = "/no-access";

  /** Public retry page, also used for a stale CSRF token. */
  private static final String STALE_PAGE = "/sign-in-failed";

  /** Default behaviour: sets 403 and lets the error machinery take it from there. */
  private final AccessDeniedHandler forApiCallers = new AccessDeniedHandlerImpl();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
      throws IOException, ServletException {

    if (request.getRequestURI().startsWith("/api/")) {
      forApiCallers.handle(request, response, denied);
      return;
    }

    // A rejected CSRF token is not an entitlement problem, and labelling it "No
    // access to /logout" sends the reader looking for a missing role that does not
    // exist. It means the page was stale — the session rotated or the app restarted
    // since it was rendered — so it belongs on the retry page.
    if (denied instanceof CsrfException) {
      response.sendRedirect(request.getContextPath() + STALE_PAGE + "?reason=stale");
      return;
    }

    // The attempted path is passed along so the page can name the product the user
    // was refused, rather than showing a generic "access denied".
    String attempted = URLEncoder.encode(request.getRequestURI(), StandardCharsets.UTF_8);
    response.sendRedirect(request.getContextPath() + DENIED_PAGE + "?path=" + attempted);
  }
}
