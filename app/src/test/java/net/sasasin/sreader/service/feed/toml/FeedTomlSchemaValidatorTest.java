package net.sasasin.sreader.service.feed.toml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FeedTomlSchemaValidatorTest {

  private final FeedTomlParser parser = new FeedTomlParser();
  private final FeedTomlSchemaValidator validator = new FeedTomlSchemaValidator();

  @Test
  void requiresIntegerSchemaVersion() {
    FeedTomlSchemaValidator.Result missing = validate("[[feeds]]\n");
    assertThat(missing.schemaVersion()).isEmpty();
    assertThat(missing.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("schema_version is required"));

    FeedTomlSchemaValidator.Result quoted = validate("schema_version = \"2\"\n");
    assertThat(quoted.schemaVersion()).isEmpty();
    assertThat(quoted.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("must be an integer"));

    FeedTomlSchemaValidator.Result floating = validate("schema_version = 2.0\n");
    assertThat(floating.schemaVersion()).isEmpty();

    FeedTomlSchemaValidator.Result ok = validate("schema_version = 2\n");
    assertThat(ok.schemaVersion()).hasValue(2);
    assertThat(ok.feeds()).isEmpty();
  }

  @Test
  void treatsMissingFeedsAsEmptyAndRejectsNonArrayFeeds() {
    FeedTomlSchemaValidator.Result missingFeeds = validate("schema_version = 1\n");
    assertThat(missingFeeds.feeds()).isEmpty();
    assertThat(missingFeeds.issues()).isEmpty();

    FeedTomlSchemaValidator.Result empty =
        validate(
            """
            schema_version = 1
            feeds = []
            """);
    assertThat(empty.feeds()).isEmpty();

    FeedTomlSchemaValidator.Result stringFeeds =
        validate(
            """
            schema_version = 1
            feeds = "nope"
            """);
    assertThat(stringFeeds.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("feeds must be an array"));
  }

  @Test
  void validatesFeedFieldTypesAndAllowsUnknownKeys() {
    FeedTomlSchemaValidator.Result result =
        validate(
            """
            schema_version = 2
            extra_root = true
            [[feeds]]
            url = "https://example.com/a.xml"
            status = "active"
            unknown_feed_key = 1
            [[feeds]]
            url = 12
            status = true
            unsubscribe_reason = 1
            unsubscribed_at = 2026-06-14T12:00:00+09:00
            note = false
            full_text_method = 3
            """);

    assertThat(result.feeds()).hasSize(2);
    assertThat(result.feeds().getFirst().url()).contains("https://example.com/a.xml");
    assertThat(result.feeds().get(1).url()).isEmpty();
    assertThat(result.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("feeds[2].url must be a string"))
        .anyMatch(m -> m.contains("feeds[2].status must be a string"))
        .anyMatch(m -> m.contains("feeds[2].unsubscribe_reason must be a string"))
        .anyMatch(m -> m.contains("feeds[2].unsubscribed_at must be a string"))
        .anyMatch(m -> m.contains("feeds[2].note must be a string"))
        .anyMatch(m -> m.contains("feeds[2].full_text_method must be a string"));
  }

  @Test
  void requiresUrlButPassesBlankUrlToMapper() {
    FeedTomlSchemaValidator.Result missingUrl =
        validate(
            """
            schema_version = 1
            [[feeds]]
            status = "active"
            """);
    assertThat(missingUrl.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("url is required"));

    FeedTomlSchemaValidator.Result blankUrl =
        validate(
            """
            schema_version = 1
            [[feeds]]
            url = "   "
            """);
    assertThat(blankUrl.issues()).isEmpty();
    assertThat(blankUrl.feeds().getFirst().url()).contains("   ");
  }

  @Test
  void preservesFeedOrderAndReturnsImmutableCollections() {
    FeedTomlSchemaValidator.Result result =
        validate(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.com/1.xml"
            [[feeds]]
            url = "https://example.com/2.xml"
            """);
    assertThat(result.feeds()).extracting(FeedTomlEntry::index).containsExactly(0, 1);
    assertThatThrownBy(() -> result.feeds().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.issues().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void ignoresGeneratedAtForImportModel() {
    FeedTomlSchemaValidator.Result result =
        validate(
            """
            schema_version = 2
            generated_at = "2026-06-14T12:00:00+09:00"
            """);
    assertThat(result.schemaVersion()).hasValue(2);
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void rejectsOutOfRangeSchemaVersionAndNonTableFeedElements() {
    FeedTomlSchemaValidator.Result huge = validate("schema_version = 3000000000\n");
    assertThat(huge.schemaVersion()).isEmpty();
    assertThat(huge.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("out of range"));

    FeedTomlSchemaValidator.Result nonTable =
        validate(
            """
            schema_version = 2
            feeds = ["not-a-table"]
            """);
    assertThat(nonTable.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("must be a table"));
  }

  @Test
  void rejectsBooleanAndTableSchemaVersion() {
    assertThat(validate("schema_version = true\n").schemaVersion()).isEmpty();
    assertThat(validate("schema_version = { n = 1 }\n").schemaVersion()).isEmpty();
  }

  private FeedTomlSchemaValidator.Result validate(String toml) {
    return validator.validate(parser.parse(toml).document());
  }
}
