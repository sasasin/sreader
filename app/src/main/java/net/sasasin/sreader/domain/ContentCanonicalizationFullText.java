package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Snapshot of a full-text row attached to a canonicalization candidate. */
public record ContentCanonicalizationFullText(
    String id, String text, OffsetDateTime extractedAt, OffsetDateTime createdAt) {

  public ContentCanonicalizationFullText {
    Objects.requireNonNull(id, "id must not be null");
    // text may be blank or null in DB; blank rows are not selected as survivor full text
    Objects.requireNonNull(extractedAt, "extractedAt must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
