package com.karaka.orbit.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** A branch office employees are posted to. */
public class Branch {

  private String code;
  private String name;
  private String city;
  private LocalDate openedOn;

  protected Branch() {}

  public Branch(String code, String name, String city, LocalDate openedOn) {
    this.code = Objects.requireNonNull(code, "code").strip().toUpperCase(Locale.ROOT);
    this.name = Objects.requireNonNull(name, "name").strip();
    this.city = Objects.requireNonNull(city, "city").strip();
    this.openedOn = Objects.requireNonNull(openedOn, "openedOn");
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getCity() {
    return city;
  }

  public LocalDate getOpenedOn() {
    return openedOn;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Branch that && Objects.equals(code, that.code);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(code);
  }

  @Override
  public String toString() {
    return "Branch[" + code + ", " + name + "]";
  }
}
