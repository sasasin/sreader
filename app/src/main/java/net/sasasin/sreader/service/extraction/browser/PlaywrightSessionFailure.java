package net.sasasin.sreader.service.extraction.browser;

import java.util.Objects;
import net.sasasin.sreader.service.outcome.OperationFailure;

/** Runtime bridge that preserves a structured operation failure through session cleanup. */
public final class PlaywrightSessionFailure extends RuntimeException {

  private final OperationFailure failure;

  public PlaywrightSessionFailure(OperationFailure failure) {
    super(failure.message(), failure.cause().orElse(null));
    this.failure = Objects.requireNonNull(failure, "failure must not be null");
  }

  public OperationFailure failure() {
    return failure;
  }
}
