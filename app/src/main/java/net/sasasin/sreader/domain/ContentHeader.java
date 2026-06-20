package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;

public record ContentHeader(
    String id,
    String feedUrlId,
    String url,
    String title,
    OffsetDateTime publishedAt,
    String feedText) {

  public ContentHeader(
      String id, String feedUrlId, String url, String title, OffsetDateTime publishedAt) {
    this(id, feedUrlId, url, title, publishedAt, null);
  }
}
