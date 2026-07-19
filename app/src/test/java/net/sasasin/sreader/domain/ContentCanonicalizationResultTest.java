package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentCanonicalizationResultTest {

  @Test
  void namedUpdatesPreserveEveryCounter() {
    ContentCanonicalizationResult result =
        ContentCanonicalizationResult.empty()
            .incrementScannedRows()
            .addUnchangedRows(2)
            .addPlannedGroup(false, true)
            .addMergedRows(3, 4, 5)
            .withFileResult(6, 7, 8)
            .withFailedGroup();

    assertThat(result)
        .isEqualTo(new ContentCanonicalizationResult(1, 2, 1, 0, 3, 4, 5, 1, 1, 6, 7, 8, 1));
    assertThat(result.hasFailures()).isTrue();
  }

  @Test
  void plannedMergeAndEmptyResultHaveExpectedCounters() {
    ContentCanonicalizationResult result =
        ContentCanonicalizationResult.empty().addPlannedGroup(true, false);

    assertThat(result)
        .isEqualTo(new ContentCanonicalizationResult(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0));
    assertThat(result.hasFailures()).isFalse();
  }
}
