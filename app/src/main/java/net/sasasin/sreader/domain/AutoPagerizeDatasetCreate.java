package net.sasasin.sreader.domain;

import java.util.Objects;

/**
 * Insert payload for a new AutoPagerize dataset. Identity is ({@code format}, {@code sourceSha256},
 * {@code importerVersion}).
 */
public record AutoPagerizeDatasetCreate(
    String format,
    String sourceFilename,
    String sourceUri,
    String sourceSha256,
    int importerVersion,
    int inputItemCount,
    int acceptedRuleCount,
    int rejectedRuleCount,
    String metadataJson) {

  public AutoPagerizeDatasetCreate {
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(sourceSha256, "sourceSha256 must not be null");
    if (metadataJson == null || metadataJson.isBlank()) {
      metadataJson = "{}";
    }
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

  public AutoPagerizeDatasetCreate(
      String format,
      String sourceFilename,
      String sourceUri,
      String sourceSha256,
      int importerVersion,
      int inputItemCount,
      int acceptedRuleCount,
      int rejectedRuleCount) {
    this(
        format,
        sourceFilename,
        sourceUri,
        sourceSha256,
        importerVersion,
        inputItemCount,
        acceptedRuleCount,
        rejectedRuleCount,
        "{}");
  }
}
