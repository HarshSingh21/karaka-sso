package com.karaka.orbit.model;

/**
 * Where an employee stands in the register.
 *
 * <p>{@link #EXITED} is terminal: it is the only status that carries an exit
 * date, and an exited record cannot be moved back to active. Rehiring is a new
 * record, not an edit — otherwise the register loses the fact that someone left.
 */
public enum EmploymentStatus {
  /** Currently employed and working. */
  ACTIVE,

  /** Still employed but not currently working — long leave, sabbatical, suspension. */
  INACTIVE,

  /** No longer employed. Terminal. */
  EXITED;

  public boolean isTerminal() {
    return this == EXITED;
  }
}
