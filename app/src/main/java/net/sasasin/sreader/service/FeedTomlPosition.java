package net.sasasin.sreader.service;

import java.util.OptionalInt;

/**
 * 1-based human-oriented source position. Absence is explicit rather than a null position object.
 */
record FeedTomlPosition(OptionalInt line, OptionalInt column) {

  static final FeedTomlPosition UNKNOWN =
      new FeedTomlPosition(OptionalInt.empty(), OptionalInt.empty());

  FeedTomlPosition {
    if (line.isPresent() != column.isPresent()) {
      throw new IllegalArgumentException("line and column must both be present or both absent");
    }
    if (line.isPresent() && line.getAsInt() < 1) {
      throw new IllegalArgumentException("line must be >= 1");
    }
    if (column.isPresent() && column.getAsInt() < 1) {
      throw new IllegalArgumentException("column must be >= 1");
    }
  }

  static FeedTomlPosition of(int line, int column) {
    return new FeedTomlPosition(OptionalInt.of(line), OptionalInt.of(column));
  }

  static FeedTomlPosition unknown() {
    return UNKNOWN;
  }

  boolean isKnown() {
    return line.isPresent();
  }

  int lineOrMax() {
    return line.orElse(Integer.MAX_VALUE);
  }

  int columnOrMax() {
    return column.orElse(Integer.MAX_VALUE);
  }
}
