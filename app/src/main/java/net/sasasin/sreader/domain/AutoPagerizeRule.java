package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Persisted AutoPagerize SITEINFO rule before runtime compilation. Does not hold compiled {@link
 * java.util.regex.Pattern} or XPath expressions.
 */
public record AutoPagerizeRule(
    long datasetId,
    int ordinal,
    int matchOrder,
    String externalId,
    String resourceUrl,
    String name,
    String createdBy,
    OffsetDateTime sourceCreatedAt,
    OffsetDateTime sourceUpdatedAt,
    String urlPattern,
    String nextLinkXpath,
    String pageElementXpath,
    String insertBeforeXpath,
    String exampleUrl,
    String rawItemJson) {

  public AutoPagerizeRule {
    Objects.requireNonNull(urlPattern, "urlPattern must not be null");
    Objects.requireNonNull(nextLinkXpath, "nextLinkXpath must not be null");
    Objects.requireNonNull(pageElementXpath, "pageElementXpath must not be null");
    Objects.requireNonNull(rawItemJson, "rawItemJson must not be null");
    if (ordinal < 0) {
      throw new IllegalArgumentException("ordinal must be >= 0");
    }
    if (matchOrder < 0) {
      throw new IllegalArgumentException("matchOrder must be >= 0");
    }
    if (urlPattern.isBlank()) {
      throw new IllegalArgumentException("urlPattern must not be blank");
    }
    if (nextLinkXpath.isBlank()) {
      throw new IllegalArgumentException("nextLinkXpath must not be blank");
    }
    if (pageElementXpath.isBlank()) {
      throw new IllegalArgumentException("pageElementXpath must not be blank");
    }
    if (rawItemJson.isBlank()) {
      throw new IllegalArgumentException("rawItemJson must not be blank");
    }
  }
}
