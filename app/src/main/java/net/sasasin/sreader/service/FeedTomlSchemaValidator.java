package net.sasasin.sreader.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

/**
 * Validates sreader feed-file shape and TOML value types on a TomlJ tree. URL normalization, enum
 * conversion, and ImportFeed construction are left to {@link FeedTomlImportMapper}.
 *
 * <p>Unknown root/feed keys are intentionally accepted for wire compatibility; the writer never
 * re-emits them. {@code generated_at} is optional and unused by import. {@code unsubscribed_at}
 * remains a quoted string for compatibility (native TOML datetime is rejected).
 *
 * <p>When TomlJ returns a partial tree after syntax recovery, only keys that can be read safely are
 * validated. Missing-field checks run when a feed table itself recovered; cascade errors for
 * unreadable nodes are skipped so syntax diagnostics remain primary.
 */
final class FeedTomlSchemaValidator {

  record Result(OptionalInt schemaVersion, List<FeedTomlEntry> feeds, List<FeedTomlIssue> issues) {
    Result {
      feeds = List.copyOf(Objects.requireNonNull(feeds, "feeds"));
      issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }
  }

  Result validate(TomlParseResult document) {
    Objects.requireNonNull(document, "document must not be null");
    List<FeedTomlIssue> issues = new ArrayList<>();
    OptionalInt schemaVersion = readSchemaVersion(document, issues);
    List<FeedTomlEntry> feeds = new ArrayList<>();
    readFeeds(document, feeds, issues);
    return new Result(schemaVersion, feeds, issues);
  }

  private OptionalInt readSchemaVersion(TomlParseResult document, List<FeedTomlIssue> issues) {
    if (!document.contains("schema_version")) {
      issues.add(
          FeedTomlIssue.schema(
              FeedTomlPosition.of(1, 1),
              "schema_version",
              "schema_version is required",
              issues.size()));
      return OptionalInt.empty();
    }
    FeedTomlPosition position = positionOf(document, "schema_version");
    if (!document.isLong("schema_version")) {
      issues.add(
          FeedTomlIssue.schema(
              position, "schema_version", "schema_version must be an integer", issues.size()));
      return OptionalInt.empty();
    }
    long value = document.getLong("schema_version");
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      issues.add(
          FeedTomlIssue.schema(
              position,
              "schema_version",
              "schema_version is out of range for a 32-bit integer: " + value,
              issues.size()));
      return OptionalInt.empty();
    }
    return OptionalInt.of((int) value);
  }

  private void readFeeds(
      TomlParseResult document, List<FeedTomlEntry> feeds, List<FeedTomlIssue> issues) {
    if (!document.contains("feeds")) {
      return;
    }
    if (!document.isArray("feeds")) {
      issues.add(
          FeedTomlIssue.schema(
              positionOf(document, "feeds"),
              "feeds",
              "feeds must be an array of tables",
              issues.size()));
      return;
    }
    TomlArray array = document.getArray("feeds");
    if (array == null) {
      return;
    }
    for (int i = 0; i < array.size(); i++) {
      readFeed(array, i, feeds, issues);
    }
  }

  private void readFeed(
      TomlArray array, int index, List<FeedTomlEntry> feeds, List<FeedTomlIssue> issues) {
    String pathPrefix = "feeds[" + (index + 1) + "]";
    Object raw = array.get(index);
    if (!(raw instanceof TomlTable table)) {
      issues.add(
          FeedTomlIssue.schema(
              FeedTomlPosition.unknown(),
              pathPrefix,
              pathPrefix + " must be a table",
              issues.size()));
      return;
    }
    FeedTomlPosition tablePosition = positionOf(table);
    Optional<String> url = Optional.empty();
    FeedTomlPosition urlPosition = tablePosition;
    if (!table.contains("url")) {
      issues.add(
          FeedTomlIssue.schema(
              tablePosition, pathPrefix + ".url", pathPrefix + ".url is required", issues.size()));
    } else if (!table.isString("url")) {
      issues.add(
          FeedTomlIssue.schema(
              positionOf(table, "url"),
              pathPrefix + ".url",
              pathPrefix + ".url must be a string",
              issues.size()));
    } else {
      url = Optional.ofNullable(table.getString("url"));
      urlPosition = positionOf(table, "url");
    }

    Optional<String> status = optionalString(table, "status", pathPrefix, issues);
    Optional<String> reason = optionalString(table, "unsubscribe_reason", pathPrefix, issues);
    Optional<String> unsubscribedAt = optionalUnsubscribedAt(table, pathPrefix, issues);
    Optional<String> note = optionalString(table, "note", pathPrefix, issues);
    Optional<String> method = optionalString(table, "full_text_method", pathPrefix, issues);

    feeds.add(
        new FeedTomlEntry(
            index,
            url,
            status,
            reason,
            unsubscribedAt,
            note,
            method,
            tablePosition,
            urlPosition,
            fieldPositions(table)));
  }

  private static Map<String, FeedTomlPosition> fieldPositions(TomlTable table) {
    return table.keySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                key -> key, key -> positionOf(table, key)));
  }

  private Optional<String> optionalUnsubscribedAt(
      TomlTable table, String pathPrefix, List<FeedTomlIssue> issues) {
    if (!table.contains("unsubscribed_at")) {
      return Optional.empty();
    }
    String fieldPath = pathPrefix + ".unsubscribed_at";
    // Compatibility: accept only TOML string, not native datetime values.
    if (!table.isString("unsubscribed_at")) {
      issues.add(
          FeedTomlIssue.schema(
              positionOf(table, "unsubscribed_at"),
              fieldPath,
              fieldPath + " must be a string",
              issues.size()));
      return Optional.empty();
    }
    return Optional.ofNullable(table.getString("unsubscribed_at"));
  }

  private Optional<String> optionalString(
      TomlTable table, String key, String pathPrefix, List<FeedTomlIssue> issues) {
    if (!table.contains(key)) {
      return Optional.empty();
    }
    String fieldPath = pathPrefix + "." + key;
    if (!table.isString(key)) {
      issues.add(
          FeedTomlIssue.schema(
              positionOf(table, key), fieldPath, fieldPath + " must be a string", issues.size()));
      return Optional.empty();
    }
    return Optional.ofNullable(table.getString(key));
  }

  private static FeedTomlPosition positionOf(TomlTable table, String key) {
    TomlPosition position = table.inputPositionOf(key);
    if (position != null) {
      return FeedTomlPosition.of(position.line(), position.column());
    }
    return positionOf(table);
  }

  private static FeedTomlPosition positionOf(TomlTable table) {
    for (String key : table.keySet()) {
      TomlPosition position = table.inputPositionOf(key);
      if (position != null) {
        return FeedTomlPosition.of(position.line(), position.column());
      }
    }
    return FeedTomlPosition.unknown();
  }
}
