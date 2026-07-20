package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Header values to write for the surviving article after a group is planned. Does not carry full
 * text fields; selected full text is tracked separately on the plan. {@code updatedAt} is set by
 * the repository at write time.
 */
public record ContentCanonicalizationSurvivor(
    String id,
    String feedUrlId,
    String sourceUrl,
    String fetchUrl,
    String canonicalUrl,
    String title,
    OffsetDateTime publishedAt,
    String feedText,
    OffsetDateTime createdAt) {

  public ContentCanonicalizationSurvivor {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(feedUrlId, "feedUrlId must not be null");
    Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
    Objects.requireNonNull(fetchUrl, "fetchUrl must not be null");
    Objects.requireNonNull(canonicalUrl, "canonicalUrl must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
