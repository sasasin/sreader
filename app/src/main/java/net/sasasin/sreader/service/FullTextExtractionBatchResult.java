package net.sasasin.sreader.service;

import java.util.Objects;
import java.util.Optional;

/** Aggregated outcome of a pending full-text extraction batch. */
public record FullTextExtractionBatchResult(
    int selectedTargets,
    int inserted,
    int alreadyPresent,
    int noContent,
    int skipped,
    int failed,
    Optional<BatchStopReason> stopReason) {

  public FullTextExtractionBatchResult {
    selectedTargets = OutcomePreconditions.requireNonNegative("selectedTargets", selectedTargets);
    inserted = OutcomePreconditions.requireNonNegative("inserted", inserted);
    alreadyPresent = OutcomePreconditions.requireNonNegative("alreadyPresent", alreadyPresent);
    noContent = OutcomePreconditions.requireNonNegative("noContent", noContent);
    skipped = OutcomePreconditions.requireNonNegative("skipped", skipped);
    failed = OutcomePreconditions.requireNonNegative("failed", failed);
    stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
    int accounted = inserted + alreadyPresent + noContent + skipped + failed;
    if (accounted > selectedTargets) {
      throw new IllegalArgumentException(
          "outcome counts (" + accounted + ") exceed selectedTargets (" + selectedTargets + ")");
    }
  }

  public static FullTextExtractionBatchResult empty() {
    return new FullTextExtractionBatchResult(0, 0, 0, 0, 0, 0, Optional.empty());
  }
}
