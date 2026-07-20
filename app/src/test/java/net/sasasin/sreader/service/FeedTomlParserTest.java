package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

class FeedTomlParserTest {

  private final FeedTomlParser parser = new FeedTomlParser();

  @Test
  void rejectsNullInput() {
    assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void parsesEmptyDocumentWithoutSyntaxIssues() {
    ParsedFeedToml parsed = parser.parse("");
    assertThat(parsed.syntaxIssues()).isEmpty();
    assertThat(parsed.document().isEmpty()).isTrue();
  }

  @Test
  void parsesCanonicalSchemaDocument() {
    ParsedFeedToml parsed =
        parser.parse(
            """
            schema_version = 2
            generated_at = "2026-06-14T12:00:00+09:00"

            [[feeds]]
            url = "https://example.com/feed.xml"
            status = "active"
            full_text_method = "http"
            """);
    assertThat(parsed.syntaxIssues()).isEmpty();
    assertThat(parsed.document().getLong("schema_version")).isEqualTo(2L);
    TomlArray feeds = parsed.document().getArray("feeds");
    assertThat(feeds.size()).isEqualTo(1);
    assertThat(feeds.getTable(0).getString("url")).isEqualTo("https://example.com/feed.xml");
  }

  @Test
  void acceptsCommentsAndHashInsideStrings() {
    ParsedFeedToml parsed =
        parser.parse(
            """
            # top comment
            schema_version = 2 # trailing
            [[feeds]]
            url = "https://example.com/feed#fragment" # after value
            note = "keep # hash"
            """);
    assertThat(parsed.syntaxIssues()).isEmpty();
    TomlTable feed = parsed.document().getArray("feeds").getTable(0);
    assertThat(feed.getString("url")).isEqualTo("https://example.com/feed#fragment");
    assertThat(feed.getString("note")).isEqualTo("keep # hash");
  }

  @Test
  void acceptsEqualsInsideStrings() {
    ParsedFeedToml parsed =
        parser.parse(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.com/feed"
            note = "a=b"
            """);
    assertThat(parsed.syntaxIssues()).isEmpty();
    assertThat(parsed.document().getArray("feeds").getTable(0).getString("note")).isEqualTo("a=b");
  }

  @Test
  void acceptsBasicLiteralAndMultilineStrings() {
    ParsedFeedToml parsed =
        parser.parse(
            """
            schema_version = 2
            [[feeds]]
            url = 'https://example.com/literal'
            note = \"\"\"multi
            line\"\"\"
            """);
    assertThat(parsed.syntaxIssues()).isEmpty();
    TomlTable feed = parsed.document().getArray("feeds").getTable(0);
    assertThat(feed.getString("url")).isEqualTo("https://example.com/literal");
    assertThat(feed.getString("note")).isEqualTo("multi\nline");
  }

  @Test
  void acceptsMultilineLiteralAndUnicodeEscape() {
    ParsedFeedToml parsed =
        parser.parse(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.com/feed"
            note = '''literal
            block'''
            """);
    assertThat(parsed.syntaxIssues()).isEmpty();
    assertThat(parsed.document().getArray("feeds").getTable(0).getString("note"))
        .isEqualTo("literal\nblock");

    ParsedFeedToml unicode =
        parser.parse(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.com/feed"
            note = "Japanese: \\u65E5\\u672C"
            """);
    assertThat(unicode.syntaxIssues()).isEmpty();
    assertThat(unicode.document().getArray("feeds").getTable(0).getString("note"))
        .isEqualTo("Japanese: 日本");
  }

  @Test
  void reportsMalformedAssignmentInvalidEscapeAndUnterminatedString() {
    ParsedFeedToml malformed = parser.parse("schema_version = \nnot_an_assignment\n");
    assertThat(malformed.syntaxIssues()).isNotEmpty();
    assertThat(malformed.syntaxIssues().getFirst().position().isKnown()).isTrue();

    ParsedFeedToml invalidEscape =
        parser.parse(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.com/feed"
            note = "\\x"
            """);
    assertThat(invalidEscape.syntaxIssues()).isNotEmpty();
    assertThat(invalidEscape.syntaxIssues().getFirst().kind()).isEqualTo(FeedTomlIssue.Kind.SYNTAX);
    assertThat(invalidEscape.syntaxIssues().getFirst().message())
        .isEqualTo("invalid TOML string escape");

    ParsedFeedToml unterminated =
        parser.parse(
            """
            schema_version = 2
            [[feeds]]
            url = "unterminated
            """);
    assertThat(unterminated.syntaxIssues()).isNotEmpty();
  }

  @Test
  void reportsDuplicateKeysAndKeepsIssueListImmutable() {
    ParsedFeedToml duplicate =
        parser.parse(
            """
            schema_version = 2
            schema_version = 1
            """);
    assertThat(duplicate.syntaxIssues()).isNotEmpty();
    assertThat(duplicate.syntaxIssues().getFirst().message()).isEqualTo("duplicate TOML key");
    assertThatThrownBy(() -> duplicate.syntaxIssues().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
