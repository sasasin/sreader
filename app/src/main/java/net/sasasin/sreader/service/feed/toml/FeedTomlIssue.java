package net.sasasin.sreader.service.feed.toml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Unified diagnostic for feed TOML import. Kind separates syntax (TomlJ), schema shape/types, and
 * domain conversion.
 */
record FeedTomlIssue(
    Kind kind, FeedTomlPosition position, String path, String message, int sequence) {

  enum Kind {
    SYNTAX,
    SCHEMA,
    DOMAIN
  }

  private static final Comparator<FeedTomlIssue> ORDER =
      Comparator.comparing((FeedTomlIssue issue) -> issue.position().isKnown() ? 0 : 1)
          .thenComparingInt(issue -> issue.position().lineOrMax())
          .thenComparingInt(issue -> issue.position().columnOrMax())
          .thenComparingInt(issue -> issue.kind().ordinal())
          .thenComparingInt(FeedTomlIssue::sequence);

  FeedTomlIssue {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(position, "position must not be null");
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }

  static FeedTomlIssue syntax(FeedTomlPosition position, String message, int sequence) {
    return new FeedTomlIssue(Kind.SYNTAX, position, null, message, sequence);
  }

  static FeedTomlIssue schema(
      FeedTomlPosition position, String path, String message, int sequence) {
    return new FeedTomlIssue(Kind.SCHEMA, position, path, message, sequence);
  }

  static FeedTomlIssue domain(
      FeedTomlPosition position, String path, String message, int sequence) {
    return new FeedTomlIssue(Kind.DOMAIN, position, path, message, sequence);
  }

  String render() {
    // message already carries the stable public wording (including path when relevant).
    if (!position.isKnown()) {
      return message;
    }
    return "line "
        + position.line().getAsInt()
        + ", column "
        + position.column().getAsInt()
        + ": "
        + message;
  }

  static List<FeedTomlIssue> sortedCopy(List<FeedTomlIssue> issues) {
    List<FeedTomlIssue> copy = new ArrayList<>(Objects.requireNonNull(issues, "issues"));
    copy.sort(ORDER);
    return List.copyOf(copy);
  }

  static List<String> renderAll(List<FeedTomlIssue> issues) {
    return sortedCopy(issues).stream().map(FeedTomlIssue::render).toList();
  }
}
