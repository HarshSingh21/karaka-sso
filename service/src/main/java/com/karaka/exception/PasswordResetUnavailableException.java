package com.karaka.exception;

/**
 * Keycloak could not be reached, or the realm is configured such that a reset cannot proceed.
 *
 * <p>Separate from a "no such user" result on purpose: an outage and an unknown username look
 * the same to a caller unless the distinction is kept, and conflating them is how a broken
 * service-account secret gets reported to every user as "no account found".
 */
public class PasswordResetUnavailableException extends RuntimeException {

  public PasswordResetUnavailableException(String message) {
    super(message);
  }

  public PasswordResetUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
