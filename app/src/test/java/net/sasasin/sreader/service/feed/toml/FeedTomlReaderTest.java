package net.sasasin.sreader.service.feed.toml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;

class FeedTomlReaderTest {

  private final FeedTomlReader reader = new FeedTomlReader();

  @Test
  void readsValidDocumentEndToEnd() {
    List<FeedTomlService.ImportFeed> feeds =
        reader.read(
            """
            schema_version = 2
            [[feeds]]
            url = 'https://example.com/feed'
            note = \"\"\"hello\"\"\"
            """);
    assertThat(feeds)
        .singleElement()
        .extracting(
            FeedTomlService.ImportFeed::url,
            FeedTomlService.ImportFeed::status,
            FeedTomlService.ImportFeed::fullTextMethod)
        .containsExactly("https://example.com/feed", FeedStatus.ACTIVE, FullTextMethod.HTTP);
  }

  @Test
  void aggregatesSyntaxSchemaAndDomainIssuesDeterministically() {
    FeedTomlService.TomlValidationException exception =
        (FeedTomlService.TomlValidationException)
            org.junit.jupiter.api.Assertions.assertThrows(
                FeedTomlService.TomlValidationException.class,
                () ->
                    reader.read(
                        """
                        schema_version = "1"
                        not an assignment
                        [[feeds]]
                        url = "https://example.com/a.xml"
                        status = "ACTIVE"
                        note = "\\x"
                        [[feeds]]
                        url = "https://example.com/a/../a.xml"
                        """));

    assertThat(exception.errors()).isNotEmpty();
    assertThat(exception.errors())
        .anyMatch(
            e -> e.contains("schema_version must be an integer") || e.contains("schema_version"))
        .anyMatch(e -> e.contains("status must be active or unsubscribed") || e.contains("ACTIVE"));
    // syntax issues are present (invalid assignment and/or escape)
    assertThat(exception.errors().stream().anyMatch(e -> e.startsWith("line "))).isTrue();
    assertThat(exception).hasMessage(String.join("; ", exception.errors()));
  }

  @Test
  void acceptsInlineTableArrayEquivalentWhenTomlJBuildsFeedsArray() {
    List<FeedTomlService.ImportFeed> feeds =
        reader.read(
            """
            schema_version = 2
            feeds = [{ url = "https://example.com/inline.xml", status = "active", full_text_method = "http" }]
            """);
    assertThat(feeds)
        .singleElement()
        .extracting(FeedTomlService.ImportFeed::url)
        .isEqualTo("https://example.com/inline.xml");
  }

  @Test
  void rejectsNullInput() {
    assertThatThrownBy(() -> reader.read(null)).isInstanceOf(NullPointerException.class);
  }
}
