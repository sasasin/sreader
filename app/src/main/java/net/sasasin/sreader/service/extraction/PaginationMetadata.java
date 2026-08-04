package net.sasasin.sreader.service.extraction;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;

/**
 * AutoPagerize pagination metadata carried with extraction outcomes. DB persistence stores a
 * subset; full page URL traces are for probe diagnostics only.
 */
public record PaginationMetadata(
    long datasetId,
    String datasetSha256,
    int importerVersion,
    boolean explicitDatasetSelection,
    Optional<Integer> ruleOrdinal,
    Optional<String> ruleName,
    Optional<String> urlPattern,
    Optional<String> nextLinkXpath,
    Optional<String> pageElementXpath,
    int pageCount,
    PaginationStopReason stopReason,
    boolean complete,
    List<PaginationPageTrace> pages,
    Optional<URI> failedRequestedUrl,
    List<PageTextContribution> contributions) {

  public PaginationMetadata {
    Objects.requireNonNull(datasetSha256, "datasetSha256 must not be null");
    if (!datasetSha256.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("datasetSha256 must be 64 lowercase hex characters");
    }
    if (importerVersion < 1) {
      throw new IllegalArgumentException("importerVersion must be >= 1");
    }
    Objects.requireNonNull(ruleOrdinal, "ruleOrdinal must not be null");
    Objects.requireNonNull(ruleName, "ruleName must not be null");
    Objects.requireNonNull(urlPattern, "urlPattern must not be null");
    Objects.requireNonNull(nextLinkXpath, "nextLinkXpath must not be null");
    Objects.requireNonNull(pageElementXpath, "pageElementXpath must not be null");
    if (pageCount < 0) {
      throw new IllegalArgumentException("pageCount must be >= 0");
    }
    Objects.requireNonNull(stopReason, "stopReason must not be null");
    Objects.requireNonNull(pages, "pages must not be null");
    Objects.requireNonNull(failedRequestedUrl, "failedRequestedUrl must not be null");
    Objects.requireNonNull(contributions, "contributions must not be null");
    pages = List.copyOf(pages);
    contributions = List.copyOf(contributions);
  }

  public Optional<URI> lastPageFinalUrl() {
    if (pages.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(pages.getLast().finalUri());
  }

  public long totalBytes() {
    return pages.stream().mapToLong(PaginationPageTrace::byteSize).sum();
  }
}
