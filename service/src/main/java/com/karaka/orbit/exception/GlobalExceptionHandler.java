package com.karaka.orbit.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place where exceptions become HTTP responses.
 *
 * <p>Everything is an RFC 9457 {@link ProblemDetail} so the API has exactly one
 * error shape. Controllers therefore contain no try/catch: they call the service
 * and let it throw.
 *
 * <p>Field-level validation failures are flattened into a {@code errors} map
 * keyed by field name, which is what the ORBIT form needs to put a message under
 * the offending input rather than showing one generic banner.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String BASE_TYPE = "https://karaka.dev/problems/";

  @ExceptionHandler(EmployeeNotFoundException.class)
  ProblemDetail onNotFound(EmployeeNotFoundException ex) {
    var problem = problem(HttpStatus.NOT_FOUND, "Employee not found", ex.getMessage(), "employee-not-found");
    problem.setProperty("employeeId", ex.getEmployeeId());
    return problem;
  }

  @ExceptionHandler(DuplicateEmailException.class)
  ProblemDetail onDuplicateEmail(DuplicateEmailException ex) {
    var problem = problem(HttpStatus.CONFLICT, "Email already in use", ex.getMessage(), "duplicate-email");
    problem.setProperty("email", ex.getEmail());
    // Surfaced under the email field so the UI can render it inline, the same
    // way a bean-validation failure on that field would be.
    problem.setProperty("errors", Map.of("email", "Already registered to another employee"));
    return problem;
  }

  @ExceptionHandler(UnknownBranchException.class)
  ProblemDetail onUnknownBranch(UnknownBranchException ex) {
    var problem =
        problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown branch", ex.getMessage(), "unknown-branch");
    problem.setProperty("errors", Map.of("branchCode", "Not a known branch"));
    return problem;
  }

  /**
   * Domain invariants defended in {@link com.karaka.orbit.model.Employee} —
   * exiting twice, reinstating an exited employee. 409: the request is valid but
   * conflicts with the record's current state.
   */
  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail onIllegalState(IllegalStateException ex) {
    return problem(HttpStatus.CONFLICT, "Not allowed in current state", ex.getMessage(), "illegal-state");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), "invalid-argument");
  }

  /**
   * 403, distinct from 401. Spring Security raises this when the user is
   * authenticated but lacks the role a {@code @PreAuthorize} demands — letting it
   * fall through to the 500 handler below would hide a permissions problem behind
   * a server error.
   */
  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail onAccessDenied(AccessDeniedException ex) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Not permitted",
        "Your account does not have the role required for this action.",
        "access-denied");
  }

  @Override
  protected org.springframework.http.ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      org.springframework.http.HttpHeaders headers,
      org.springframework.http.HttpStatusCode status,
      org.springframework.web.context.request.WebRequest request) {

    var problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
        "One or more fields are invalid.", "validation-failed");

    // LinkedHashMap keeps declaration order, so the UI highlights fields in the
    // order they appear in the form rather than a hash order.
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    problem.setProperty("errors", errors);

    return org.springframework.http.ResponseEntity.badRequest().body(problem);
  }

  /**
   * Last resort. The message is deliberately generic — an exception's text can
   * carry internals — while the full stack trace goes to the log with the path
   * attached so it can still be diagnosed.
   */
  @ExceptionHandler(Exception.class)
  ProblemDetail onUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Unexpected error",
        "Something went wrong. The failure has been logged.",
        "internal-error");
  }

  private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create(BASE_TYPE + type));
    return problem;
  }
}
