package net.sasasin.sreader.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlVersion;

/**
 * TOML 1.0 syntax parsing via TomlJ. Domain schema and enum rules are handled by later pipeline
 * stages.
 */
final class FeedTomlParser {

  ParsedFeedToml parse(String input) {
    Objects.requireNonNull(input, "input must not be null");
    // Explicit TOML 1.0: sreader feed files target the stable 1.0 data model, not HEAD drafts.
    TomlParseResult result = Toml.parse(input, TomlVersion.V1_0_0);
    List<FeedTomlIssue> syntaxIssues = new ArrayList<>();
    int sequence = 0;
    for (TomlParseError error : result.errors()) {
      syntaxIssues.add(toIssue(error, sequence++));
    }
    return new ParsedFeedToml(result, syntaxIssues);
  }

  private static FeedTomlIssue toIssue(TomlParseError error, int sequence) {
    TomlPosition position = error.position();
    FeedTomlPosition feedPosition = FeedTomlPosition.of(position.line(), position.column());
    return FeedTomlIssue.syntax(feedPosition, normalizeMessage(error.getMessage()), sequence);
  }

  private static String normalizeMessage(String rawMessage) {
    if (rawMessage == null || rawMessage.isBlank()) {
      return "invalid TOML syntax";
    }
    String message = rawMessage.toLowerCase(java.util.Locale.ROOT);
    if (message.contains("duplicate")
        || message.contains("defined")
        || message.contains("already exists")) {
      return "duplicate TOML key";
    }
    if (message.contains("escape")) {
      return "invalid TOML string escape";
    }
    if (message.contains("unterminated")) {
      return "unterminated TOML string";
    }
    return "invalid TOML syntax";
  }
}
