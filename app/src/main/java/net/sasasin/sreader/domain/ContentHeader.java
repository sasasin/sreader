package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;

public record ContentHeader(
    String id, String feedUrlId, String url, String title, OffsetDateTime publishedAt) {}
