package com.karaka.exception;

/** Thrown when an email is already on another employee. Mapped to HTTP 409. */
public class DuplicateEmailException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String email;

  public DuplicateEmailException(String email) {
    super("Email already registered to another employee: " + email);
    this.email = email;
  }

  public String getEmail() {
    return email;
  }
}
