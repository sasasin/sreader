package net.sasasin.sreader.service.outcome;

import java.util.Objects;

/** Shared constructor guards for operation outcome types. */
public final class OutcomePreconditions {

  private OutcomePreconditions() {}

  public static String requireNonBlank(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  public static int requireNonNegative(String name, int value) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must be non-negative: " + value);
    }
    return value;
  }
}
