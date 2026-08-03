package net.sasasin.sreader.service.autopagerize;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class PaginationUriSupportTest {

  @Test
  void forVisitedComparisonDropsFragmentAndNormalizesHostPort() {
    URI withFragment = URI.create("HTTPS://Example.COM:443/a/../b#section");
    URI canonical = PaginationUriSupport.forVisitedComparison(withFragment);
    assertThat(canonical.getScheme()).isEqualTo("https");
    assertThat(canonical.getHost()).isEqualTo("example.com");
    assertThat(canonical.getPort()).isEqualTo(443);
    assertThat(canonical.getPath()).isEqualTo("/b");
    assertThat(canonical.getFragment()).isNull();
  }

  @Test
  void sameOriginComparesSchemeHostAndEffectivePort() {
    assertThat(
            PaginationUriSupport.sameOrigin(
                URI.create("https://example.com/a"), URI.create("https://example.com:443/b")))
        .isTrue();
    assertThat(
            PaginationUriSupport.sameOrigin(
                URI.create("https://example.com/a"), URI.create("http://example.com/a")))
        .isFalse();
    assertThat(
            PaginationUriSupport.sameOrigin(
                URI.create("https://example.com/a"), URI.create("https://other.example/a")))
        .isFalse();
  }

  @Test
  void resolveNextCandidateUsesBaseHref() {
    Document document =
        Jsoup.parse(
            "<html><head><base href=\"https://cdn.example.com/dir/\"></head><body></body></html>",
            "https://example.com/articles/1");
    assertThat(
            PaginationUriSupport.resolveNextCandidate(
                URI.create("https://example.com/articles/1"), document, "page2.html"))
        .contains(URI.create("https://cdn.example.com/dir/page2.html"));
  }

  @Test
  void rejectsUserInfoAndNonHttpSchemes() {
    assertThat(PaginationUriSupport.hasUserInfo(URI.create("https://user:pass@example.com/")))
        .isTrue();
    assertThat(PaginationUriSupport.isAllowedScheme(URI.create("ftp://example.com/a"))).isFalse();
    assertThat(PaginationUriSupport.isAllowedScheme(URI.create("https://example.com/a"))).isTrue();
  }

  @Test
  void forVisitedComparisonFillsEmptyPathAndPreservesQuery() {
    URI uri = URI.create("http://Example.COM?q=1#frag");
    URI canonical = PaginationUriSupport.forVisitedComparison(uri);
    assertThat(canonical.getPath()).isEqualTo("/");
    assertThat(canonical.getQuery()).isEqualTo("q=1");
    assertThat(canonical.getPort()).isEqualTo(80);
  }

  @Test
  void resolveNextWithoutBaseUsesFinalUri() {
    Document document = Jsoup.parse("<html><body></body></html>", "https://example.com/dir/page");
    assertThat(
            PaginationUriSupport.resolveNextCandidate(
                URI.create("https://example.com/dir/page"), document, "../next"))
        .contains(URI.create("https://example.com/next"));
  }

  @Test
  void firstNonBlankAttributeOrder() {
    Document document =
        Jsoup.parse(
            "<html><body><a href='' action='/act' value='/val'></a></body></html>",
            "https://example.com/");
    assertThat(PaginationUriSupport.firstNonBlankAttribute(document.selectFirst("a")))
        .contains("/act");
    Document valueOnly =
        Jsoup.parse("<html><body><a value='/only'></a></body></html>", "https://example.com/");
    assertThat(PaginationUriSupport.firstNonBlankAttribute(valueOnly.selectFirst("a")))
        .contains("/only");
    Document empty = Jsoup.parse("<html><body><a></a></body></html>", "https://example.com/");
    assertThat(PaginationUriSupport.firstNonBlankAttribute(empty.selectFirst("a"))).isEmpty();
  }

  @Test
  void sameOriginAndPortsAndOpaqueSchemes() {
    assertThat(
            PaginationUriSupport.sameOrigin(
                URI.create("https://example.com:8443/a"), URI.create("https://example.com:8443/b")))
        .isTrue();
    assertThat(
            PaginationUriSupport.sameOrigin(
                URI.create("https://example.com:8443/a"), URI.create("https://example.com:9443/a")))
        .isFalse();
    URI mailto = URI.create("mailto:user@example.com");
    assertThat(PaginationUriSupport.sameOrigin(mailto, mailto)).isTrue();
    assertThat(PaginationUriSupport.effectivePort("ftp", -1)).isEqualTo(-1);
  }

  @Test
  void resolveNextCandidateEmptyOnIllegalUri() {
    Document document = Jsoup.parse("<html><body></body></html>", "https://example.com/");
    assertThat(
            PaginationUriSupport.resolveNextCandidate(
                URI.create("https://example.com/"), document, "http://[::invalid"))
        .isEmpty();
    assertThat(
            PaginationUriSupport.resolveNextCandidate(
                URI.create("https://example.com/"), document, null))
        .isEmpty();
  }

  @Test
  void allowedSchemeAndUserInfoEdgeCases() {
    assertThat(PaginationUriSupport.isAllowedScheme(URI.create("file:///tmp/x"))).isFalse();
    assertThat(PaginationUriSupport.isAllowedScheme(URI.create("HTTP://example.com/"))).isTrue();
    assertThat(PaginationUriSupport.hasUserInfo(URI.create("https://example.com/x"))).isFalse();
    assertThat(PaginationUriSupport.hasUserInfo(URI.create("https://u@example.com/x"))).isTrue();
  }
}
