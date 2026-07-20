package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * One content header row loaded for canonicalization, with optional attached full text. Absence of
 * full text is {@link Optional#empty()}, never null fields on a shared flat record.
 */
public record ContentCanonicalizationCandidate(
    ContentCanonicalizationHeader header, Optional<ContentCanonicalizationFullText> fullText) {

  public ContentCanonicalizationCandidate {
    Objects.requireNonNull(header, "header must not be null");
    Objects.requireNonNull(fullText, "fullText must not be null");
  }

  public String id() {
    return header.id();
  }

  public String feedUrlId() {
    return header.feedUrlId();
  }

  public String sourceUrl() {
    return header.sourceUrl();
  }

  public String fetchUrl() {
    return header.fetchUrl();
  }

  public String canonicalUrl() {
    return header.canonicalUrl();
  }

  public String title() {
    return header.title();
  }

  public OffsetDateTime publishedAt() {
    return header.publishedAt();
  }

  public String feedText() {
    return header.feedText();
  }

  public OffsetDateTime createdAt() {
    return header.createdAt();
  }

  public OffsetDateTime updatedAt() {
    return header.updatedAt();
  }
}
