package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArticleUrlCanonicalizerTest {

  private final ArticleUrlCanonicalizer canonicalizer =
      new ArticleUrlCanonicalizer("publisher.example.test", "/articles/");

  @ParameterizedTest
  @MethodSource("canonicalizationCases")
  void canonicalizesOnlyConfiguredArticleGsParameters(String input, String expected) {
    URI canonical = canonicalizer.canonicalize(URI.create(input));

    assertThat(canonical).hasToString(expected);
    assertThat(canonicalizer.canonicalize(canonical)).isEqualTo(canonical);
  }

  private static Stream<Arguments> canonicalizationCases() {
    return Stream.of(
        Arguments.of(
            "https://publisher.example.test/articles/123?gs=aaa",
            "https://publisher.example.test/articles/123"),
        Arguments.of(
            "https://publisher.example.test/articles/123?x=1&gs=aaa&y=2",
            "https://publisher.example.test/articles/123?x=1&y=2"),
        Arguments.of(
            "https://PUBLISHER.EXAMPLE.TEST/articles/123?gs=aaa&gs=bbb",
            "https://PUBLISHER.EXAMPLE.TEST/articles/123"),
        Arguments.of(
            "https://other.example.test/article?gs=meaningful",
            "https://other.example.test/article?gs=meaningful"),
        Arguments.of(
            "https://publisher.example.test/other/path?gs=meaningful",
            "https://publisher.example.test/other/path?gs=meaningful"),
        Arguments.of(
            "https://publisher.example.test/articles/123?x=1#section",
            "https://publisher.example.test/articles/123?x=1"),
        Arguments.of(
            "https://publisher.example.test/articles/123?x=a%2Bb&gs=aaa",
            "https://publisher.example.test/articles/123?x=a%2Bb"));
  }
}
