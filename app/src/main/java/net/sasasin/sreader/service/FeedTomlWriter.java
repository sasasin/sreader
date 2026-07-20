package net.sasasin.sreader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;

/**
 * Deterministic sreader feed-file formatter. Deliberately not TomlJ table serialization: field
 * order, {@code [[feeds]]} layout, blank lines, LF, and optional-field omission must stay stable
 * for Git-friendly exports.
 */
final class FeedTomlWriter {

  String write(List<FeedUrl> feeds, Clock clock) {
    Objects.requireNonNull(feeds, "feeds must not be null");
    Objects.requireNonNull(clock, "clock must not be null");
    StringBuilder toml = new StringBuilder("schema_version = 2\n");
    toml.append("generated_at = \"").append(OffsetDateTime.now(clock)).append("\"\n");
    for (FeedUrl feed : feeds) {
      Objects.requireNonNull(feed, "feed must not be null");
      toml.append("\n[[feeds]]\nurl = \"").append(escapeBasicString(feed.url())).append("\"\n");
      toml.append("status = \"").append(feed.status().value()).append("\"\n");
      toml.append("full_text_method = \"").append(feed.fullTextMethod().value()).append("\"\n");
      if (feed.status() == FeedStatus.UNSUBSCRIBED) {
        appendString(
            toml,
            "unsubscribe_reason",
            feed.unsubscribeReason() == null ? null : feed.unsubscribeReason().value());
        if (feed.unsubscribedAt() != null) {
          toml.append("unsubscribed_at = \"").append(feed.unsubscribedAt()).append("\"\n");
        }
        appendString(toml, "note", feed.note());
      }
    }
    return toml.toString();
  }

  private void appendString(StringBuilder toml, String key, String value) {
    if (value != null) {
      toml.append(key).append(" = \"").append(escapeBasicString(value)).append("\"\n");
    }
  }

  /**
   * Escapes a value for a TOML basic string. TomlJ {@code tomlEscape} rewrites non-ASCII to Unicode
   * escapes and is therefore not used: canonical export keeps UTF-8 literals for Japanese/emoji.
   */
  static String escapeBasicString(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\b", "\\b")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\f", "\\f")
        .replace("\r", "\\r");
  }
}
