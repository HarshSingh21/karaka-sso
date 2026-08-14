package com.karaka.service;

import com.karaka.utils.KeycloakAdminClient.KeycloakUser;
import java.time.Clock;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import com.karaka.config.PasswordResetProperties;
import com.karaka.exception.PasswordResetUnavailableException;
import com.karaka.utils.SlidingWindowRateLimiter;
import com.karaka.utils.KeycloakAdminClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Self-service password reset that tells the user when no account exists.
 *
 * <p>This is a deliberate departure from Keycloak's default, which answers identically for a
 * known and an unknown user so the form cannot be used to enumerate accounts. The product
 * decision here is that a mistyped username should say so, because these accounts are created
 * by an administrator and a silent non-answer leaves the user with nothing to act on.
 *
 * <p>What that costs, stated plainly: this endpoint confirms which usernames exist. The
 * mitigation is the two rate limiters below — by caller IP, and separately by identifier so one
 * account cannot be mail-bombed from many addresses.
 */
@Service
@Slf4j
public class PasswordResetService {

  private final KeycloakAdminClient keycloak;
  private final PasswordResetProperties config;
  private final SlidingWindowRateLimiter perCaller;
  private final SlidingWindowRateLimiter perIdentifier;

  public PasswordResetService(KeycloakAdminClient keycloak, PasswordResetProperties config, Clock clock) {
    this.keycloak = keycloak;
    this.config = config;
    this.perCaller = new SlidingWindowRateLimiter(clock, config.maxPerWindow(), config.window());
    this.perIdentifier =
        new SlidingWindowRateLimiter(clock, config.maxPerWindow(), config.window());
  }

  /** What happened, in terms the UI can render without re-deriving anything. */
  public enum Outcome {
    /** A reset link is on its way. */
    SENT,
    /** No account matches. This is the case Keycloak's own flow refuses to disclose. */
    NO_ACCOUNT,
    /** The account exists but carries no email address, so nothing can be sent. */
    NO_EMAIL,
    /** The account exists but is disabled; a reset would not let them in. */
    DISABLED,
    /** Too many attempts from this caller or for this identifier. */
    RATE_LIMITED,
    /** Keycloak could not be reached, or the realm is misconfigured. */
    UNAVAILABLE
  }

  /**
   * @param identifier username or email, as typed
   * @param callerIp   used only for rate limiting, never stored
   * @param redirectUri where Keycloak returns the user after the password is set; must be a
   *                    registered redirect URI for the configured client
   */
  public Outcome requestReset(String identifier, String callerIp, String redirectUri) {
    String normalised = identifier.strip();
    if (normalised.isEmpty()) {
      return Outcome.NO_ACCOUNT;
    }
    if (!config.isConfigured()) {
      // No service-account secret: fail closed and say so, rather than reporting NO_ACCOUNT
      // for every user and having support chase a phantom data problem.
      log.warn("password reset requested but karaka.password-reset.admin-client-secret is unset");
      return Outcome.UNAVAILABLE;
    }
    // Both limits, and the IP one first: it is the axis an attacker controls least.
    if (!perCaller.tryAcquire(callerIp)
        || !perIdentifier.tryAcquire(normalised.toLowerCase(java.util.Locale.ROOT))) {
      log.info("password reset rate-limited for caller {}", callerIp);
      return Outcome.RATE_LIMITED;
    }

    Optional<KeycloakUser> found;
    try {
      found = keycloak.findByUsernameOrEmail(normalised);
    } catch (PasswordResetUnavailableException | RestClientException e) {
      log.error("password reset lookup failed", e);
      return Outcome.UNAVAILABLE;
    }
    if (found.isEmpty()) {
      return Outcome.NO_ACCOUNT;
    }
    KeycloakUser user = found.get();
    if (!user.enabled()) {
      return Outcome.DISABLED;
    }
    if (!user.canReceiveEmail()) {
      return Outcome.NO_EMAIL;
    }
    try {
      keycloak.sendPasswordResetEmail(user.id(), redirectUri);
    } catch (PasswordResetUnavailableException | RestClientException e) {
      log.error("could not send reset email to user {}", user.id(), e);
      return Outcome.UNAVAILABLE;
    }
    log.info("password reset email sent for user {}", user.username());
    return Outcome.SENT;
  }

  /** Keeps the limiter maps from growing with every unique caller IP seen. */
  @Scheduled(fixedDelayString = "PT5M")
  public void evictIdleRateLimitEntries() {
    perCaller.evictIdle();
    perIdentifier.evictIdle();
  }
}
