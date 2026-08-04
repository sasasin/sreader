package net.sasasin.sreader.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Successful full-text row for persistence. Failure rows are not represented; partial AutoPagerize
 * text is never stored.
 */
public record ContentFullText(
    String id,
    String contentHeaderId,
    String fullText,
    String extractionMethod,
    String extractionStatus,
    String errorMessage,
    String sourceKind,
    String extractedUrl,
    Long autopagerizeDatasetId,
    Integer autopagerizeRuleOrdinal,
    Integer paginationPageCount,
    String paginationStopReason,
    Boolean paginationComplete) {

  public static final String STATUS_SUCCESS = "success";

  public ContentFullText {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(contentHeaderId, "contentHeaderId must not be null");
    Objects.requireNonNull(fullText, "fullText must not be null");
    Objects.requireNonNull(extractionMethod, "extractionMethod must not be null");
    if (extractionMethod.isBlank()) {
      throw new IllegalArgumentException("extractionMethod must not be blank");
    }
    Objects.requireNonNull(extractionStatus, "extractionStatus must not be null");
    if (extractionStatus.isBlank()) {
      throw new IllegalArgumentException("extractionStatus must not be blank");
    }
    Objects.requireNonNull(sourceKind, "sourceKind must not be null");
    if (sourceKind.isBlank()) {
      throw new IllegalArgumentException("sourceKind must not be blank");
    }
    Objects.requireNonNull(extractedUrl, "extractedUrl must not be null");
    if (extractedUrl.isBlank()) {
      throw new IllegalArgumentException("extractedUrl must not be blank");
    }
    if (autopagerizeRuleOrdinal != null && autopagerizeDatasetId == null) {
      throw new IllegalArgumentException("autopagerizeRuleOrdinal requires autopagerizeDatasetId");
    }
    if (paginationPageCount != null && paginationPageCount < 1) {
      throw new IllegalArgumentException("paginationPageCount must be >= 1 when present");
    }
  }

  /** Successful non-AutoPagerize insert (or feed-entry text). All AutoPagerize columns are null. */
  public static ContentFullText success(
      String id,
      String contentHeaderId,
      String fullText,
      String extractionMethod,
      String sourceKind,
      String extractedUrl) {
    return new ContentFullText(
        id,
        contentHeaderId,
        fullText,
        extractionMethod,
        STATUS_SUCCESS,
        null,
        sourceKind,
        extractedUrl,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Successful AutoPagerize insert, including single-page fallback after {@code NO_MATCHING_RULE}.
   */
  public static ContentFullText successAutopagerize(
      String id,
      String contentHeaderId,
      String fullText,
      String extractionMethod,
      String sourceKind,
      String extractedUrl,
      long datasetId,
      Optional<Integer> ruleOrdinal,
      int pageCount,
      String stopReason,
      boolean complete) {
    Objects.requireNonNull(ruleOrdinal, "ruleOrdinal must not be null");
    Objects.requireNonNull(stopReason, "stopReason must not be null");
    if (stopReason.isBlank()) {
      throw new IllegalArgumentException("stopReason must not be blank");
    }
    if (pageCount < 1) {
      throw new IllegalArgumentException("pageCount must be >= 1");
    }
    return new ContentFullText(
        id,
        contentHeaderId,
        fullText,
        extractionMethod,
        STATUS_SUCCESS,
        null,
        sourceKind,
        extractedUrl,
        datasetId,
        ruleOrdinal.orElse(null),
        pageCount,
        stopReason,
        complete);
  }
}
