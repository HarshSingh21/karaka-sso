package com.karaka.exception;

/**
 * Thrown when an employee references a branch that does not exist. Mapped to
 * HTTP 422 — the request was well-formed but refers to something that is not
 * there, which is not the same failure as a malformed field (400).
 */
public class UnknownBranchException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownBranchException(String branchCode) {
    super("No such branch: " + branchCode);  }
}
