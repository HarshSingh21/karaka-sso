package com.karaka.controller;

import com.karaka.utils.AuthenticatedActor;
import com.karaka.dto.EmployeeRequest;
import com.karaka.dto.EmployeeResponse;
import com.karaka.dto.ExitRequest;
import com.karaka.dto.StatusRequest;
import com.karaka.model.enums.EmploymentStatus;
import com.karaka.service.EmployeeData;
import com.karaka.service.EmployeeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.karaka.exception.GlobalExceptionHandler;
import lombok.RequiredArgsConstructor;

/**
 * The ORBIT employee register.
 *
 * <p>Reachable by both callers: a browser session via the BFF chain, or a
 * {@code Bearer} token via the machine chain. Neither is special-cased here —
 * authorization is expressed once, as roles, and works the same either way.
 *
 * <p>Two roles, deliberately separated. {@code ORBIT_VIEW} reads;
 * {@code ORBIT_MANAGE} writes. A single {@code ORBIT_USER} role would mean every
 * account that can look up a colleague's desk can also exit them from the
 * register.
 *
 * <p>No try/catch anywhere: the service throws, and
 * {@code GlobalExceptionHandler} turns those into RFC 9457 responses.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

  private final EmployeeService employees;


  /**
   * @param search matched against name, email, id and title
   * @param branch branch code, exact match
   * @param status employment status, exact match
   */
  @GetMapping
  @PreAuthorize("hasRole('ORBIT_VIEW')")
  List<EmployeeResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String branch,
      @RequestParam(required = false) EmploymentStatus status) {
    return employees.findAll(search, branch, status).stream().map(EmployeeResponse::from).toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ORBIT_VIEW')")
  EmployeeResponse get(@PathVariable String id) {
    return EmployeeResponse.from(employees.findById(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ORBIT_MANAGE')")
  EmployeeResponse create(
      @Valid @RequestBody EmployeeRequest request, Authentication authentication) {
    var created = employees.create(toData(request), AuthenticatedActor.usernameOf(authentication));
    return EmployeeResponse.from(created);
  }

  /** Full replacement of the editable fields. Status and id are not editable here. */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ORBIT_MANAGE')")
  EmployeeResponse update(
      @PathVariable String id,
      @Valid @RequestBody EmployeeRequest request,
      Authentication authentication) {
    var updated =
        employees.update(id, toData(request), AuthenticatedActor.usernameOf(authentication));
    return EmployeeResponse.from(updated);
  }

  /**
   * Records that an employee has left.
   *
   * <p>POST to a sub-resource rather than DELETE: the record is kept, which is the
   * entire purpose of a register. DELETE would imply the row disappears and take
   * the employment history with it.
   */
  @PostMapping("/{id}/exit")
  @PreAuthorize("hasRole('ORBIT_MANAGE')")
  EmployeeResponse exit(
      @PathVariable String id,
      @Valid @RequestBody(required = false) ExitRequest request,
      Authentication authentication) {
    var exitOn = request == null ? null : request.exitOn();
    var exited = employees.exit(id, exitOn, AuthenticatedActor.usernameOf(authentication));
    return EmployeeResponse.from(exited);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ORBIT_MANAGE')")
  EmployeeResponse changeStatus(
      @PathVariable String id,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    var updated =
        employees.changeStatus(
            id, request.status(), AuthenticatedActor.usernameOf(authentication));
    return EmployeeResponse.from(updated);
  }

  private EmployeeData toData(EmployeeRequest request) {
    return new EmployeeData(
        request.fullName(),
        request.email(),
        request.branchCode(),
        request.title(),
        request.joinedOn());
  }
}
