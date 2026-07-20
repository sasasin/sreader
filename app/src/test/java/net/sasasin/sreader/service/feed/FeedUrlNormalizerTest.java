package net.sasasin.sreader.service.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FeedUrlNormalizerTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n", "# comment", "  # leading whitespace comment"})
  void normalizeSeedLineReturnsNullForBlankOrComment(String input) {
    assertThat(FeedUrlNormalizer.normalizeSeedLine(input)).isNull();
  }

  @Test
  void normalizeSeedLineReturnsValidUrl() {
    assertThat(FeedUrlNormalizer.normalizeSeedLine("https://example.com/feed.xml"))
        .isEqualTo("https://example.com/feed.xml");
  }

  @Test
  void normalizeSeedLineUsesOnlyUrlBeforeTabAndLabel() {
    assertThat(FeedUrlNormalizer.normalizeSeedLine("https://example.com/feed.xml\tMy Feed"))
        .isEqualTo("https://example.com/feed.xml");
  }

  @Test
  void normalizeSeedLineHandlesMultipleTabs() {
    assertThat(FeedUrlNormalizer.normalizeSeedLine("https://example.com/a\tb\tc"))
        .isEqualTo("https://example.com/a");
  }

  @Test
  void normalizeSeedLineTrimsSurroundingWhitespace() {
    assertThat(FeedUrlNormalizer.normalizeSeedLine("  https://example.com/feed  "))
        .isEqualTo("https://example.com/feed");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://[invalid",
        "/relative/path",
        "ftp://example.com/feed",
        "https://user:pass@example.com/feed"
      })
  void normalizeSeedLineReturnsNullForInvalidUri(String input) {
    assertThat(FeedUrlNormalizer.normalizeSeedLine(input)).isNull();
  }

  @Test
  void normalizeSeedLineNormalizesPath() {
    assertThat(FeedUrlNormalizer.normalizeSeedLine("https://example.com/a/../b"))
        .isEqualTo("https://example.com/b");
  }

  @Test
  void normalizeStrictRejectsNull() {
    assertThatThrownBy(() -> FeedUrlNormalizer.normalizeStrict(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("url is required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t"})
  void normalizeStrictRejectsBlank(String input) {
    assertThatThrownBy(() -> FeedUrlNormalizer.normalizeStrict(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("url must not be blank");
  }

  @Test
  void normalizeStrictRejectsMalformedUriAndKeepsCause() {
    assertThatThrownBy(() -> FeedUrlNormalizer.normalizeStrict("http://[invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url must be a valid absolute URI")
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void normalizeStrictRejectsRelativeUri() {
    assertThatThrownBy(() -> FeedUrlNormalizer.normalizeStrict("/relative/path"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url must be an absolute http or https URI");
  }

  @Test
  void normalizeStrictRejectsAbsoluteUriWithoutHost() {
    assertThatThrownBy(() -> FeedUrlNormalizer.normalizeStrict("http:///no-host"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url must be an absolute http or https URI");
  }

  @Test
  void normalizeStrictRejectsUnsupportedScheme() {
    assertThatThrownBy(() -> FeedUrlNormalizer.normalizeStrict("ftp://example.com/feed"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url scheme must be http or https");
  }

  @Test
  void normalizeStrictRejectsUserInfo() {
    assertThatThrownBy(
            () -> FeedUrlNormalizer.normalizeStrict("https://user:pass@example.com/feed"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url must not include userinfo");
  }

  @Test
  void normalizeStrictRejectsEmptyUserInfo() {
    assertThatThrownBy(() -> FeedUrlNormalizer.normalizeStrict("http://@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url must not include userinfo");
  }

  @Test
  void normalizeStrictAcceptsHttp() {
    assertThat(FeedUrlNormalizer.normalizeStrict("http://example.com/feed"))
        .isEqualTo("http://example.com/feed");
  }

  @Test
  void normalizeStrictAcceptsHttps() {
    assertThat(FeedUrlNormalizer.normalizeStrict("https://example.com/feed"))
        .isEqualTo("https://example.com/feed");
  }

  @Test
  void normalizeStrictAcceptsSchemeCaseInsensitive() {
    assertThat(FeedUrlNormalizer.normalizeStrict("HtTpS://Example.COM/feed"))
        .isEqualTo("HtTpS://Example.COM/feed");
  }

  @Test
  void normalizeStrictPreservesPortQueryAndFragment() {
    assertThat(
            FeedUrlNormalizer.normalizeStrict("https://example.com:8443/path?query=value#fragment"))
        .isEqualTo("https://example.com:8443/path?query=value#fragment");
  }

  @Test
  void normalizeStrictNormalizesAndTrims() {
    assertThat(FeedUrlNormalizer.normalizeStrict("  https://example.com/a/../b  "))
        .isEqualTo("https://example.com/b");
  }
}
