package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.sasasin.sreader.domain.FullTextMethod.Definition;
import net.sasasin.sreader.domain.FullTextMethod.HtmlExtractor;
import net.sasasin.sreader.domain.FullTextMethod.PlaywrightMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class FullTextMethodTest {

  @Test
  void wireValuesMatchExpectedExactValuesInDeclarationOrder() {
    assertThat(FullTextMethod.wireValues())
        .containsExactly(
            "feed",
            "http",
            "http_readability",
            "playwright",
            "playwright_readability",
            "playwright_infy_scroll",
            "playwright_infy_scroll_readability");
    assertThat(FullTextMethod.wireValues()).isUnmodifiable();
    assertThat(FullTextMethod.supportedValues())
        .isEqualTo(String.join(", ", FullTextMethod.wireValues()));
  }

  @ParameterizedTest
  @EnumSource(FullTextMethod.class)
  void fromValueRoundTripsEveryMethod(FullTextMethod method) {
    assertThat(FullTextMethod.fromValue(method.value())).isEqualTo(method);
  }

  @Test
  void rejectsUnknownAndNullWireValues() {
    assertThatThrownBy(() -> FullTextMethod.fromValue("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported full text method");
    assertThatThrownBy(() -> FullTextMethod.fromValue(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FullTextMethod.fromValue("HTTP"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultMethodIsHttp() {
    assertThat(FullTextMethod.defaultMethod()).isEqualTo(FullTextMethod.HTTP);
    assertThat(FullTextMethod.defaultMethod().value()).isEqualTo("http");
  }

  @Test
  void definitionMappingIsExactForAllConstants() {
    assertThat(FullTextMethod.FEED.definition()).isInstanceOf(Definition.FeedEntry.class);
    assertThat(FullTextMethod.HTTP.definition())
        .isEqualTo(new Definition.HttpArticle(HtmlExtractor.XPATH_OR_BODY_TEXT));
    assertThat(FullTextMethod.HTTP_READABILITY.definition())
        .isEqualTo(new Definition.HttpArticle(HtmlExtractor.READABILITY));
    assertThat(FullTextMethod.PLAYWRIGHT.definition())
        .isEqualTo(
            new Definition.PlaywrightArticle(
                PlaywrightMode.STANDARD, HtmlExtractor.XPATH_OR_BODY_TEXT));
    assertThat(FullTextMethod.PLAYWRIGHT_READABILITY.definition())
        .isEqualTo(
            new Definition.PlaywrightArticle(PlaywrightMode.STANDARD, HtmlExtractor.READABILITY));
    assertThat(FullTextMethod.PLAYWRIGHT_INFY_SCROLL.definition())
        .isEqualTo(
            new Definition.PlaywrightArticle(
                PlaywrightMode.INFY_SCROLL, HtmlExtractor.XPATH_OR_BODY_TEXT));
    assertThat(FullTextMethod.PLAYWRIGHT_INFY_SCROLL_READABILITY.definition())
        .isEqualTo(
            new Definition.PlaywrightArticle(
                PlaywrightMode.INFY_SCROLL, HtmlExtractor.READABILITY));
  }

  @Test
  void feedCapabilities() {
    FullTextMethod feed = FullTextMethod.FEED;
    assertThat(feed.usesFeedEntryContent()).isTrue();
    assertThat(feed.requiresEntryLink()).isFalse();
    assertThat(feed.supportsArticleProbe()).isFalse();
    assertThat(feed.supportsXpathOverride()).isFalse();
    assertThat(feed.requiresPlaywright()).isFalse();
    assertThat(feed.articleDefinition()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("httpMethods")
  void httpCapabilities(FullTextMethod method) {
    assertThat(method.usesFeedEntryContent()).isFalse();
    assertThat(method.requiresEntryLink()).isTrue();
    assertThat(method.supportsArticleProbe()).isTrue();
    assertThat(method.supportsXpathOverride()).isTrue();
    assertThat(method.requiresPlaywright()).isFalse();
    assertThat(method.articleDefinition()).containsInstanceOf(Definition.HttpArticle.class);
  }

  @ParameterizedTest
  @MethodSource("playwrightMethods")
  void playwrightCapabilities(FullTextMethod method) {
    assertThat(method.usesFeedEntryContent()).isFalse();
    assertThat(method.requiresEntryLink()).isTrue();
    assertThat(method.supportsArticleProbe()).isTrue();
    assertThat(method.supportsXpathOverride()).isTrue();
    assertThat(method.requiresPlaywright()).isTrue();
    assertThat(method.articleDefinition()).containsInstanceOf(Definition.PlaywrightArticle.class);
  }

  @Test
  void nestedRecordsRejectNullComponents() {
    assertThatThrownBy(() -> new Definition.HttpArticle(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("extractor");
    assertThatThrownBy(() -> new Definition.PlaywrightArticle(null, HtmlExtractor.READABILITY))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("mode");
    assertThatThrownBy(() -> new Definition.PlaywrightArticle(PlaywrightMode.STANDARD, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("extractor");
  }

  @Test
  void wireValuesAreUniqueAndMatchEnumSize() {
    List<String> values = FullTextMethod.wireValues();
    assertThat(values).hasSize(FullTextMethod.values().length);
    assertThat(values).doesNotHaveDuplicates();
    assertThat(Arrays.stream(FullTextMethod.values()).map(FullTextMethod::value).toList())
        .isEqualTo(values);
  }

  static Stream<FullTextMethod> httpMethods() {
    return Stream.of(FullTextMethod.HTTP, FullTextMethod.HTTP_READABILITY);
  }

  static Stream<FullTextMethod> playwrightMethods() {
    return Stream.of(
        FullTextMethod.PLAYWRIGHT,
        FullTextMethod.PLAYWRIGHT_READABILITY,
        FullTextMethod.PLAYWRIGHT_INFY_SCROLL,
        FullTextMethod.PLAYWRIGHT_INFY_SCROLL_READABILITY);
  }

  static Stream<Arguments> methodDefinitions() {
    return Arrays.stream(FullTextMethod.values())
        .map(method -> Arguments.of(method, method.definition()));
  }

  @ParameterizedTest
  @MethodSource("methodDefinitions")
  void everyConstantHasNonNullDefinition(FullTextMethod method, Definition definition) {
    assertThat(definition).isNotNull();
    assertThat(method.definition()).isSameAs(definition);
  }
}
