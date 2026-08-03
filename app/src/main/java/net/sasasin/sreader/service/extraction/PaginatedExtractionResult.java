package net.sasasin.sreader.service.extraction;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of page-by-page text extraction over a successful AutoPagerize pagination result, with
 * per-page source metadata.
 */
public record PaginatedExtractionResult(
    TextExtractionOutcome outcome, List<PageTextContribution> contributions) {

  public PaginatedExtractionResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(contributions, "contributions must not be null");
    contributions = List.copyOf(contributions);
  }

  public boolean mixedSources() {
    return contributions.stream().map(PageTextContribution::source).distinct().count() > 1;
  }
}
