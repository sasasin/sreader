package net.sasasin.sreader.service.autopagerize;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** One top-level SITEINFO array element after field extraction and validation. */
public record AutoPagerizeParsedItem(
    int ordinal,
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
    String rawItemJson,
    List<AutoPagerizeIssue> errors,
    List<AutoPagerizeIssue> warnings) {

  public AutoPagerizeParsedItem {
    Objects.requireNonNull(rawItemJson, "rawItemJson must not be null");
    Objects.requireNonNull(errors, "errors must not be null");
    Objects.requireNonNull(warnings, "warnings must not be null");
    if (ordinal < 0) {
      throw new IllegalArgumentException("ordinal must be >= 0");
    }
    if (rawItemJson.isBlank()) {
      throw new IllegalArgumentException("rawItemJson must not be blank");
    }
    errors = List.copyOf(errors);
    warnings = List.copyOf(warnings);
  }

  public boolean accepted() {
    return errors.isEmpty();
  }
}
