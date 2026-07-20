package net.sasasin.sreader.service;

import java.util.List;
import java.util.Objects;
import org.tomlj.TomlParseResult;

/**
 * Package-private parse product. TomlJ types stay inside the TOML pipeline and never leave the
 * service package's public API.
 */
record ParsedFeedToml(TomlParseResult document, List<FeedTomlIssue> syntaxIssues) {

  ParsedFeedToml {
    Objects.requireNonNull(document, "document must not be null");
    syntaxIssues =
        List.copyOf(Objects.requireNonNull(syntaxIssues, "syntaxIssues must not be null"));
  }
}
