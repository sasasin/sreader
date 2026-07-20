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

class FeedTomlWriterTest {

  private final FeedTomlWriter writer = new FeedTomlWriter();
  private final FeedTomlReader reader = new FeedTomlReader();
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-06-14T03:00:00Z"), ZoneOffset.ofHours(9));

  @Test
  void writesCanonicalGoldenDocument() {
    String toml =
        writer.write(
            List.of(
                new FeedUrl(
                    "1",
                    "https://example.com/feed.xml",
                    FeedStatus.ACTIVE,
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP),
                new FeedUrl(
                    "2",
                    "https://old.example.com/feed.xml",
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.SITE_CLOSED,
                    OffsetDateTime.parse("2026-06-14T12:00:00+09:00"),
                    "site closed",
                    FullTextMethod.FEED)),
            clock);

    assertThat(toml)
        .isEqualTo(
            """
            schema_version = 2
            generated_at = "2026-06-14T12:00+09:00"

            [[feeds]]
            url = "https://example.com/feed.xml"
            status = "active"
            full_text_method = "http"

            [[feeds]]
            url = "https://old.example.com/feed.xml"
            status = "unsubscribed"
            full_text_method = "feed"
            unsubscribe_reason = "site_closed"
            unsubscribed_at = "2026-06-14T12:00+09:00"
            note = "site closed"
            """);
  }

  @Test
  void writesEmptyFeedListWithRootMetadataOnly() {
    String toml = writer.write(List.of(), clock);
    assertThat(toml)
        .isEqualTo(
            """
            schema_version = 2
            generated_at = "2026-06-14T12:00+09:00"
            """);
    assertThat(toml).doesNotContain("[[feeds]]");
  }

  @Test
  void omitsOptionalUnsubscribedMetadataAndEscapesNote() {
    String toml =
        writer.write(
            List.of(
                new FeedUrl(
                    "id",
                    "https://example.test/feed.xml",
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.OTHER,
                    null,
                    "quote \" slash \\ LF\nCR\rTAB\t#日本語 = emoji 😀",
                    FullTextMethod.HTTP)),
            clock);
    assertThat(toml)
        .contains("unsubscribe_reason = \"other\"")
        .doesNotContain("unsubscribed_at")
        .contains("note = \"quote \\\" slash \\\\ LF\\nCR\\rTAB\\t#日本語 = emoji 😀\"");
  }

  @Test
  void roundTripsWriterOutputThroughReader() {
    String toml =
        writer.write(
            List.of(
                new FeedUrl(
                    "id",
                    "https://example.test/feed.xml",
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.MOVED,
                    OffsetDateTime.parse("2026-01-02T03:04:05Z"),
                    "quote \" slash \\ LF\nCR\rTAB\t#日本語",
                    FullTextMethod.FEED)),
            clock);
    List<FeedTomlService.ImportFeed> feeds = reader.read(toml);
    assertThat(feeds)
        .singleElement()
        .extracting(
            FeedTomlService.ImportFeed::url,
            FeedTomlService.ImportFeed::status,
            FeedTomlService.ImportFeed::unsubscribeReason,
            FeedTomlService.ImportFeed::unsubscribedAt,
            FeedTomlService.ImportFeed::note,
            FeedTomlService.ImportFeed::fullTextMethod)
        .containsExactly(
            "https://example.test/feed.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.MOVED,
            OffsetDateTime.parse("2026-01-02T03:04:05Z"),
            "quote \" slash \\ LF\nCR\rTAB\t#日本語",
            FullTextMethod.FEED);
  }

  @Test
  void rejectsNullFeedsOrClock() {
    assertThatThrownBy(() -> writer.write(null, clock)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> writer.write(List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullFeedElement() {
    List<FeedUrl> feeds = new java.util.ArrayList<>();
    feeds.add(null);
    assertThatThrownBy(() -> writer.write(feeds, clock)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void escapeBasicStringCoversControlCharacters() {
    assertThat(FeedTomlWriter.escapeBasicString("a\bb\fc")).isEqualTo("a\\bb\\fc");
  }
}
