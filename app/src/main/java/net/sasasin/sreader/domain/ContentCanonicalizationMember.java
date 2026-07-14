package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;

public record ContentCanonicalizationMember(
    String id,
    String feedUrlId,
    String sourceUrl,
    String fetchUrl,
    String canonicalUrl,
    String title,
    OffsetDateTime publishedAt,
    String feedText,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    String fullTextId,
    String fullText,
    OffsetDateTime extractedAt,
    OffsetDateTime fullTextCreatedAt) {}
