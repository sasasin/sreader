package net.sasasin.sreader.service.outcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Extra branch coverage for P0-3 outcome paths (outcome). */
class OutcomeBranchCoverageTest {

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void outcomePreconditionsAndOperationFailureFactories() {
    assertThatThrownBy(() -> OutcomePreconditions.requireNonNegative("n", -1))
        .isInstanceOf(IllegalArgumentException.class);
    OperationFailure withCause =
        OperationFailure.of(
            FailureStage.FETCH_ARTICLE, FailureKind.IO, "s", "m", new IOException("cause"));
    assertThat(withCause.cause()).isPresent();
    assertThat(withCause.interrupted()).isFalse();
  }
}
