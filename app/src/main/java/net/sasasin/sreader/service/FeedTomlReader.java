package net.sasasin.sreader.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates parse → schema validate → domain map and aggregates diagnostics. Does not access
 * repositories or plan imports.
 */
final class FeedTomlReader {

  private final FeedTomlParser parser;
  private final FeedTomlSchemaValidator validator;
  private final FeedTomlImportMapper mapper;

  FeedTomlReader() {
    this(new FeedTomlParser(), new FeedTomlSchemaValidator(), new FeedTomlImportMapper());
  }

  FeedTomlReader(
      FeedTomlParser parser, FeedTomlSchemaValidator validator, FeedTomlImportMapper mapper) {
    this.parser = Objects.requireNonNull(parser, "parser");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  List<FeedTomlService.ImportFeed> read(String input) {
    ParsedFeedToml parsed = parser.parse(input);
    FeedTomlSchemaValidator.Result validated = validator.validate(parsed.document());
    FeedTomlImportMapper.Result mapped = mapper.map(validated.schemaVersion(), validated.feeds());

    List<FeedTomlIssue> issues = new ArrayList<>();
    issues.addAll(parsed.syntaxIssues());
    issues.addAll(validated.issues());
    issues.addAll(mapped.issues());

    if (!issues.isEmpty()) {
      throw new FeedTomlService.TomlValidationException(FeedTomlIssue.renderAll(issues));
    }
    return List.copyOf(mapped.feeds());
  }
}
