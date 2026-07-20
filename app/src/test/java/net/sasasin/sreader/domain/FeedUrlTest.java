package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class FeedUrlTest {

  @Test
  void defaultConstructorIsActiveHttp() {
    FeedUrl feed = new FeedUrl("id", "https://example.test/feed.xml");
    assertThat(feed.status()).isEqualTo(FeedStatus.ACTIVE);
    assertThat(feed.unsubscribeReason()).isNull();
    assertThat(feed.unsubscribedAt()).isNull();
    assertThat(feed.note()).isNull();
    assertThat(feed.fullTextMethod()).isEqualTo(FullTextMethod.HTTP);
  }

  @Test
  void constructsActiveAndUnsubscribedFeeds() {
    FeedUrl active =
        new FeedUrl(
            "id",
            "https://example.test/a.xml",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.FEED);
    assertThat(active.status()).isEqualTo(FeedStatus.ACTIVE);
    assertThat(active.fullTextMethod()).isEqualTo(FullTextMethod.FEED);

    OffsetDateTime at = OffsetDateTime.parse("2026-01-01T00:00Z");
    FeedUrl unsubscribed =
        new FeedUrl(
            "id",
            "https://example.test/b.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.SITE_CLOSED,
            at,
            "closed",
            FullTextMethod.HTTP);
    assertThat(unsubscribed.unsubscribeReason()).isEqualTo(UnsubscribeReason.SITE_CLOSED);
    assertThat(unsubscribed.unsubscribedAt()).isEqualTo(at);
    assertThat(unsubscribed.note()).isEqualTo("closed");
  }

  @Test
  void rejectsActiveWithUnsubscribeMetadata() {
    assertThatThrownBy(
            () ->
                new FeedUrl(
                    "id",
                    "https://example.test/a.xml",
                    FeedStatus.ACTIVE,
                    UnsubscribeReason.OTHER,
                    null,
                    null,
                    FullTextMethod.HTTP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("active");
    assertThatThrownBy(
            () ->
                new FeedUrl(
                    "id",
                    "https://example.test/a.xml",
                    FeedStatus.ACTIVE,
                    null,
                    OffsetDateTime.parse("2026-01-01T00:00Z"),
                    null,
                    FullTextMethod.HTTP))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new FeedUrl(
                    "id",
                    "https://example.test/a.xml",
                    FeedStatus.ACTIVE,
                    null,
                    null,
                    "note",
                    FullTextMethod.HTTP))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnsubscribedWithoutReasonAndNullRequiredFields() {
    assertThatThrownBy(
            () ->
                new FeedUrl(
                    "id",
                    "https://example.test/a.xml",
                    FeedStatus.UNSUBSCRIBED,
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsubscribeReason");
    assertThatThrownBy(
            () ->
                new FeedUrl(
                    "id",
                    "https://example.test/a.xml",
                    null,
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("status");
    assertThatThrownBy(
            () ->
                new FeedUrl(
                    "id", "https://example.test/a.xml", FeedStatus.ACTIVE, null, null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fullTextMethod");
  }
}
