package net.sasasin.sreader.service.extraction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;

/**
 * AutoPagerize pagination metadata carried with a successful text extraction. Persistence of these
 * fields is deferred to a later phase; this type prevents information loss at the extraction
 * boundary.
 */
public record PaginationMetadata(
    long datasetId,
    Optional<Integer> ruleOrdinal,
    Optional<String> ruleName,
    int pageCount,
    PaginationStopReason stopReason,
    boolean complete,
    List<PageTextContribution> contributions) {

  public PaginationMetadata {
    Objects.requireNonNull(ruleOrdinal, "ruleOrdinal must not be null");
    Objects.requireNonNull(ruleName, "ruleName must not be null");
    if (pageCount < 1) {
      throw new IllegalArgumentException("pageCount must be >= 1");
    }
    Objects.requireNonNull(stopReason, "stopReason must not be null");
    Objects.requireNonNull(contributions, "contributions must not be null");
    contributions = List.copyOf(contributions);
  }

  public static PaginationMetadata of(
      long datasetId,
      Optional<Integer> ruleOrdinal,
      Optional<String> ruleName,
      int pageCount,
      PaginationStopReason stopReason,
      boolean complete,
      List<PageTextContribution> contributions) {
    return new PaginationMetadata(
        datasetId, ruleOrdinal, ruleName, pageCount, stopReason, complete, contributions);
  }
}
