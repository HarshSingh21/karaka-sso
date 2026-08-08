package com.karaka.orbit.dto;

import com.karaka.orbit.model.Branch;
import java.time.LocalDate;

/** A branch as the API returns it. */
public record BranchResponse(String code, String name, String city, LocalDate openedOn) {

  public static BranchResponse from(Branch branch) {
    return new BranchResponse(
        branch.getCode(), branch.getName(), branch.getCity(), branch.getOpenedOn());
  }
}
