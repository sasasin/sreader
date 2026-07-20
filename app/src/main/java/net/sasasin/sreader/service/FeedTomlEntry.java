package net.sasasin.sreader.service;

import java.util.Objects;
import java.util.Optional;

/**
 * Schema-validated raw feed table. {@code index} is 0-based input order; public error paths use
 * 1-based {@code feeds[n]}.
 */
record FeedTomlEntry(
    int index,
    Optional<String> url,
    Optional<String> status,
    Optional<String> unsubscribeReason,
    Optional<String> unsubscribedAt,
    Optional<String> note,
    Optional<String> fullTextMethod,
    FeedTomlPosition tablePosition,
    FeedTomlPosition urlPosition) {

  FeedTomlEntry {
    if (index < 0) {
      throw new IllegalArgumentException("index must be >= 0");
    }
    url = requireOptional(url, "url");
    status = requireOptional(status, "status");
    unsubscribeReason = requireOptional(unsubscribeReason, "unsubscribeReason");
    unsubscribedAt = requireOptional(unsubscribedAt, "unsubscribedAt");
    note = requireOptional(note, "note");
    fullTextMethod = requireOptional(fullTextMethod, "fullTextMethod");
    Objects.requireNonNull(tablePosition, "tablePosition must not be null");
    Objects.requireNonNull(urlPosition, "urlPosition must not be null");
  }

  private static Optional<String> requireOptional(Optional<String> value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    return value;
  }

  /** 1-based index used by ImportFeed and historical error paths. */
  int displayIndex() {
    return index + 1;
  }

  String path(String field) {
    return "feeds[" + displayIndex() + "]." + field;
  }
}
