package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;

public record ContentHeader(
    String id,
    String feedUrlId,
    String sourceUrl,
    String fetchUrl,
    String canonicalUrl,
    String title,
    OffsetDateTime publishedAt,
    String feedText) {

  public ContentHeader(
      String id, String feedUrlId, String url, String title, OffsetDateTime publishedAt) {
    this(id, feedUrlId, url, url, url, title, publishedAt, null);
  }
}
