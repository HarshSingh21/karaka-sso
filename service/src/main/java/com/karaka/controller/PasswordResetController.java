package com.karaka.controller;

import com.karaka.service.PasswordResetService.Outcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import com.karaka.dto.ForgotPasswordRequest;
import com.karaka.dto.ForgotPasswordResponse;
import com.karaka.service.PasswordResetService;
import lombok.RequiredArgsConstructor;

/**
 * Public endpoint behind the forgot-password page.
 *
 * <p>Unauthenticated by necessity — the caller cannot sign in, which is the whole point — so
 * everything protective lives in {@link PasswordResetService}: two rate limiters and a bounded
 * identifier length.
 */
@RestController
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService passwordReset;


  @PostMapping("/api/forgot-password")
  ResponseEntity<ForgotPasswordResponse> request(
      @Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest request) {

    Outcome outcome =
        passwordReset.requestReset(body.identifier(), callerIp(request), returnUri(request));

    // 429 and 503 so the status alone is actionable to a proxy or a monitor. Everything else is
    // 200: NO_ACCOUNT is a successful answer to a legitimate question, not a client error.
    HttpStatus status =
        switch (outcome) {
          case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
          case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
          default -> HttpStatus.OK;
        };
    return ResponseEntity.status(status).body(ForgotPasswordResponse.of(outcome));
  }

  /**
   * Where Keycloak returns the user once the new password is set.
   *
   * <p>Built from the incoming request rather than configured, so it is correct on localhost and
   * behind the deployment's hostname without a second setting to keep in step. Requires
   * {@code server.forward-headers-strategy=framework}, which is already set, or this would be
   * the container's own address behind a proxy.
   */
  private static String returnUri(HttpServletRequest request) {
    return UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
        .replacePath("/picker")
        .replaceQuery(null)
        .build()
        .toUriString();
  }

  /**
   * Client address for rate limiting.
   *
   * <p>{@code X-Forwarded-For} is a client-supplied header and trivially spoofed, so it is used
   * only for its <em>first</em> entry and only as a key — never for an authorization decision.
   * Behind a proxy that appends correctly this is the real client; directly exposed it is the
   * socket address.
   */
  private static String callerIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",", 2)[0].strip();
    }
    return request.getRemoteAddr();
  }
}
