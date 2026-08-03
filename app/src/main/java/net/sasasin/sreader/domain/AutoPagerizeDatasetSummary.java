package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Lightweight listing projection for AutoPagerize datasets. */
public record AutoPagerizeDatasetSummary(
    long id,
    String format,
    String sourceFilename,
    String sourceUri,
    String sourceSha256,
    int importerVersion,
    OffsetDateTime importedAt,
    int inputItemCount,
    int acceptedRuleCount,
    int rejectedRuleCount) {

  public AutoPagerizeDatasetSummary {
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(sourceSha256, "sourceSha256 must not be null");
    Objects.requireNonNull(importedAt, "importedAt must not be null");
  }
}
