package net.sasasin.sreader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;

/** TOML syntax, schema validation, and serialization; deliberately independent of persistence. */
final class FeedTomlCodec {

  String exportToml(List<FeedUrl> feeds, Clock clock) {
    StringBuilder toml = new StringBuilder("schema_version = 2\n");
    toml.append("generated_at = \"").append(OffsetDateTime.now(clock)).append("\"\n");
    for (FeedUrl feed : feeds) {
      toml.append("\n[[feeds]]\nurl = \"").append(escape(feed.url())).append("\"\n");
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

  List<FeedTomlService.ImportFeed> parse(String toml) {
    List<String> errors = new ArrayList<>();
    Map<String, String> root = new HashMap<>();
    List<Map<String, String>> tables = new ArrayList<>();
    Map<String, String> current = null;
    String[] lines = toml.split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = stripComment(lines[i]).trim();
      if (line.isEmpty()) {
        continue;
      }
      if ("[[feeds]]".equals(line)) {
        current = new HashMap<>();
        tables.add(current);
        continue;
      }
      int equals = line.indexOf('=');
      if (equals < 0) {
        errors.add("line " + (i + 1) + ": expected key = value");
        continue;
      }
      (current == null ? root : current)
          .put(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
    }
    schemaVersion(root, errors);
    Set<String> seenUrls = new HashSet<>();
    List<FeedTomlService.ImportFeed> feeds = new ArrayList<>();
    for (int i = 0; i < tables.size(); i++) {
      int index = i + 1;
      Map<String, String> table = tables.get(i);
      int errorsBefore = errors.size();
      String rawUrl = stringValue(table.get("url"), "feeds[" + index + "].url", errors);
      String url = normalizedUrl(rawUrl, index, seenUrls, errors);
      String statusValue = stringValue(table.get("status"), "feeds[" + index + "].status", errors);
      FeedStatus status = FeedStatus.ACTIVE;
      boolean statusOk = true;
      if (statusValue != null) {
        try {
          status = FeedStatus.fromValue(statusValue);
        } catch (IllegalArgumentException e) {
          errors.add("feeds[" + index + "].status must be active or unsubscribed: " + statusValue);
          statusOk = false;
        }
      }
      String reasonValue =
          stringValue(
              table.get("unsubscribe_reason"), "feeds[" + index + "].unsubscribe_reason", errors);
      UnsubscribeReason reason = null;
      boolean reasonOk = true;
      if (status == FeedStatus.UNSUBSCRIBED && reasonValue == null) {
        reason = UnsubscribeReason.OTHER;
      } else if (reasonValue != null) {
        try {
          reason = UnsubscribeReason.fromValue(reasonValue);
        } catch (IllegalArgumentException e) {
          errors.add("feeds[" + index + "].unsubscribe_reason is invalid: " + reasonValue);
          reasonOk = false;
        }
      }
      OffsetDateTime unsubscribedAt = offsetDateTime(table.get("unsubscribed_at"), index, errors);
      String note = stringValue(table.get("note"), "feeds[" + index + "].note", errors);
      FullTextMethod method = fullTextMethod(table.get("full_text_method"), index, errors);
      if (status == FeedStatus.ACTIVE) {
        // Active feeds discard unsubscribe metadata at the TOML boundary (existing external
        // behavior).
        reason = null;
        unsubscribedAt = null;
        note = null;
      }
      // Only construct a typed ImportFeed when this table has no new validation errors.
      if (url != null && statusOk && reasonOk && errors.size() == errorsBefore) {
        feeds.add(
            new FeedTomlService.ImportFeed(
                index, url, status, reason, unsubscribedAt, note, method));
      }
    }
    if (!errors.isEmpty()) {
      throw new FeedTomlService.TomlValidationException(errors);
    }
    return feeds;
  }

  private int schemaVersion(Map<String, String> root, List<String> errors) {
    String value = root.get("schema_version");
    if (value == null) {
      errors.add("schema_version is required");
      return 0;
    }
    if ("1".equals(value)) {
      return 1;
    }
    if ("2".equals(value)) {
      return 2;
    }
    errors.add("unsupported schema_version: " + value);
    return 0;
  }

  private String normalizedUrl(String raw, int index, Set<String> seen, List<String> errors) {
    if (raw == null) {
      errors.add("feeds[" + index + "].url is required");
      return null;
    }
    try {
      String url = FeedUrlNormalizer.normalizeStrict(raw);
      if (!seen.add(url)) {
        errors.add("feeds[" + index + "].url duplicates another feed after normalization: " + url);
      }
      return url;
    } catch (IllegalArgumentException e) {
      errors.add("feeds[" + index + "].url: " + e.getMessage());
      return null;
    }
  }

  private OffsetDateTime offsetDateTime(String raw, int index, List<String> errors) {
    String value = stringValue(raw, "feeds[" + index + "].unsubscribed_at", errors);
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (RuntimeException e) {
      errors.add("feeds[" + index + "].unsubscribed_at must be an offset date-time: " + value);
      return null;
    }
  }

  private FullTextMethod fullTextMethod(String raw, int index, List<String> errors) {
    String value = stringValue(raw, "feeds[" + index + "].full_text_method", errors);
    if (value == null) {
      return FullTextMethod.HTTP;
    }
    try {
      return FullTextMethod.fromValue(value);
    } catch (IllegalArgumentException e) {
      errors.add("feeds[" + index + "].full_text_method is invalid: " + value);
      return FullTextMethod.HTTP;
    }
  }

  private void appendString(StringBuilder toml, String key, String value) {
    if (value != null) {
      toml.append(key).append(" = \"").append(escape(value)).append("\"\n");
    }
  }

  private String stringValue(String value, String field, List<String> errors) {
    if (value == null) {
      return null;
    }
    if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
      errors.add(field + " must be a TOML string");
      return null;
    }
    return unescape(value.substring(1, value.length() - 1), field, errors);
  }

  private String stripComment(String line) {
    boolean inString = false;
    boolean escaping = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (escaping) {
        escaping = false;
      } else if (c == '\\' && inString) {
        escaping = true;
      } else if (c == '"') {
        inString = !inString;
      } else if (c == '#' && !inString) {
        return line.substring(0, i);
      }
    }
    return line;
  }

  private String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private String unescape(String value, String field, List<String> errors) {
    StringBuilder result = new StringBuilder();
    boolean escaping = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!escaping) {
        if (c == '\\') {
          escaping = true;
        } else {
          result.append(c);
        }
        continue;
      }
      switch (c) {
        case '\\' -> result.append('\\');
        case '"' -> result.append('"');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        default -> {
          errors.add(field + " contains unsupported escape: \\" + c);
          result.append(c);
        }
      }
      escaping = false;
    }
    if (escaping) {
      errors.add(field + " ends with an incomplete escape");
    }
    return result.toString();
  }
}
