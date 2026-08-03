package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Immutable AutoPagerize dataset metadata stored in PostgreSQL. Rules and rejections are loaded
 * separately; this type is the persistence representation, not a compiled runtime catalog.
 */
public record AutoPagerizeDataset(
    long id,
    String format,
    String sourceFilename,
    String sourceUri,
    String sourceSha256,
    int importerVersion,
    OffsetDateTime importedAt,
    int inputItemCount,
    int acceptedRuleCount,
    int rejectedRuleCount,
    String metadataJson) {

  public AutoPagerizeDataset {
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(sourceSha256, "sourceSha256 must not be null");
    Objects.requireNonNull(importedAt, "importedAt must not be null");
    Objects.requireNonNull(metadataJson, "metadataJson must not be null");
    if (format.isBlank()) {
      throw new IllegalArgumentException("format must not be blank");
    }
    if (!sourceSha256.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("sourceSha256 must be 64 lowercase hex characters");
    }
    if (importerVersion < 1) {
      throw new IllegalArgumentException("importerVersion must be >= 1");
    }
    if (inputItemCount < 0 || acceptedRuleCount < 0 || rejectedRuleCount < 0) {
      throw new IllegalArgumentException("counts must not be negative");
    }
    if (acceptedRuleCount + rejectedRuleCount != inputItemCount) {
      throw new IllegalArgumentException(
          "acceptedRuleCount + rejectedRuleCount must equal inputItemCount");
    }
  }
}
