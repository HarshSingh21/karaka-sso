package com.karaka.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

/**
 * CSRF handling for pages that read the token from the {@code XSRF-TOKEN} cookie.
 *
 * <p>Every token in this application travels the same way: JavaScript reads the
 * cookie and sends the value back verbatim, either as an {@code X-XSRF-TOKEN} header
 * or as a hidden {@code _csrf} form field. So the comparison must be plain in both
 * cases.
 *
 * <p><b>Why not XorCsrfTokenRequestAttributeHandler.</b> The XOR handler masks the
 * token differently per response, which defends against BREACH — an attack that
 * needs the token <em>rendered into a compressed response body</em>. Nothing here
 * does that: the server never writes the token into HTML, it only sets the cookie.
 * Using the XOR handler anyway means a raw cookie value arriving in a form field
 * gets un-masked into nonsense and rejected, which is exactly how every Sign out
 * button in this app came to return 403 while header-based calls kept working.
 *
 * <p>Setting the request-attribute name to {@code null} opts out of deferred token
 * loading, so the token is resolved on every request and the {@code XSRF-TOKEN}
 * cookie always exists. Without that, the cookie appears only after something else
 * touches the token and the first mutating request of a session fails.
 */
final class CookieCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

  private final CsrfTokenRequestAttributeHandler delegate = new CsrfTokenRequestAttributeHandler();

  CookieCsrfTokenRequestHandler() {
    this.delegate.setCsrfRequestAttributeName(null);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    this.delegate.handle(request, response, csrfToken);
    // Belt and braces: guarantees the repository writes the cookie on this response
    // even if the delegate's eager path changes in a future version.
    csrfToken.get();
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    // Plain for header and form field alike — see the class note above.
    return this.delegate.resolveCsrfTokenValue(request, csrfToken);
  }
}
