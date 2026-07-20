package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;
import org.junit.jupiter.api.Test;

class FeedTomlCodecTest {

  private final FeedTomlCodec codec = new FeedTomlCodec();

  @Test
  void parsesCommentsEscapesAndSchemaVersionsWithoutPersistence() {
    List<FeedTomlService.ImportFeed> feeds =
        codec.parse(
            """
            schema_version = 2 # trailing comment
            [[feeds]]
            url = "https://example.test/a/../feed.xml"
            status = "unsubscribed"
            unsubscribe_reason = "other"
            note = "line\\n# kept"
            full_text_method = "feed"
            """);

    assertThat(feeds)
        .singleElement()
        .extracting(
            FeedTomlService.ImportFeed::url,
            FeedTomlService.ImportFeed::note,
            FeedTomlService.ImportFeed::fullTextMethod)
        .containsExactly("https://example.test/feed.xml", "line\n# kept", FullTextMethod.FEED);
  }

  @Test
  void retainsValidationErrorOrderForInvalidToml() {
    assertThatThrownBy(
            () ->
                codec.parse(
                    """
                    schema_version = 9
                    [[feeds]]
                    url = relative
                    note = "bad\\x"
                    """))
        .isInstanceOf(FeedTomlService.TomlValidationException.class)
        .satisfies(
            error ->
                assertThat(((FeedTomlService.TomlValidationException) error).errors())
                    .containsExactly(
                        "unsupported schema_version: 9",
                        "feeds[1].url must be a TOML string",
                        "feeds[1].url is required",
                        "feeds[1].note contains unsupported escape: \\x"));
  }

  @Test
  void serializesFixedTimestampAndOnlyUnsubscribedMetadata() {
    String toml =
        codec.exportToml(
            List.of(
                new FeedUrl(
                    "active",
                    "https://example.test/a.xml",
                    FeedStatus.ACTIVE,
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP),
                new FeedUrl(
                    "old",
                    "https://example.test/b.xml",
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.OTHER,
                    OffsetDateTime.parse("2026-01-02T03:04:05Z"),
                    "quote \"",
                    FullTextMethod.FEED)),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(toml)
        .contains("generated_at = \"2026-01-01T00:00Z\"")
        .contains("full_text_method = \"http\"")
        .contains("note = \"quote \\\"\"")
        .doesNotContain("ignored");
  }
}
