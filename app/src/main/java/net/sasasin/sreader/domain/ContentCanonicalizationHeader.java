package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Article header fields for a canonicalization candidate loaded from the database. */
public record ContentCanonicalizationHeader(
    String id,
    String feedUrlId,
    String sourceUrl,
    String fetchUrl,
    String canonicalUrl,
    String title,
    OffsetDateTime publishedAt,
    String feedText,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public ContentCanonicalizationHeader {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(feedUrlId, "feedUrlId must not be null");
    Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
    Objects.requireNonNull(fetchUrl, "fetchUrl must not be null");
    Objects.requireNonNull(canonicalUrl, "canonicalUrl must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}
