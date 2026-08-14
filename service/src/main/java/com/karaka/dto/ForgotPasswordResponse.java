package com.karaka.dto;

import com.karaka.service.PasswordResetService.Outcome;
import com.karaka.service.PasswordResetService;

/**
 * What the page renders.
 *
 * <p>{@code outcome} is the machine-readable discriminator and {@code message} the human text,
 * so wording changes never require the page's JavaScript to change, and the page never has to
 * infer meaning from prose.
 */
public record ForgotPasswordResponse(Outcome outcome, String message) {

  public static ForgotPasswordResponse of(Outcome outcome) {
    return new ForgotPasswordResponse(outcome, messageFor(outcome));
  }

  private static String messageFor(Outcome outcome) {
    return switch (outcome) {
      case SENT ->
          "Check your email. We have sent a link to set a new password — it expires shortly.";
      case NO_ACCOUNT ->
          "No Karaka account matches that username or email. Accounts are created by your "
              + "workspace admin, so ask them to set one up or confirm the spelling.";
      case NO_EMAIL ->
          "That account has no email address on file, so we cannot send a link. Your workspace "
              + "admin can set your password directly.";
      case DISABLED ->
          "That account is disabled. Resetting the password would not restore access — contact "
              + "your workspace admin.";
      case RATE_LIMITED ->
          "Too many attempts. Wait a few minutes before trying again.";
      case UNAVAILABLE ->
          "Password reset is temporarily unavailable. Please try again shortly.";
    };
  }
}
