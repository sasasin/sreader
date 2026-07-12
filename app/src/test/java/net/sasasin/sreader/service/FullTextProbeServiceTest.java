package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ExtractionPlan.ExtractorKind;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.ProbeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FullTextProbeServiceTest {

  private static final URI ARTICLE_URL = URI.create("https://input/article");
  private static final URI FEED_URL = URI.create("https://input/feed");

  @Test
  void articleFeedMethodIsRejectedWithoutDependencies() throws Exception {
    Dependencies d = dependencies();
    assertThatThrownBy(
            () -> d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.FEED, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--method feed");
    verify(d.http, never()).get(any());
    verify(d.playwright, never()).renderPage(any(), anyBoolean());
    verify(d.extractor, never()).extract(any(), any(), any(), any());
  }

  @Test
  void articleHttpUsesResponseUrlHtmlXpathAndHtmlTitle() throws Exception {
    Dependencies d = dependencies();
    URI finalUrl = URI.create("https://final/article");
    Optional<String> xpath = Optional.of("//article");
    when(d.http.get(ARTICLE_URL))
        .thenReturn(new HttpFetchService.FetchedResource(finalUrl, "<title>Article Title</title>"));
    when(d.extractor.extract(
            finalUrl.toString(),
            "<title>Article Title</title>",
            ExtractorKind.XPATH_OR_BODY_TEXT,
            xpath))
        .thenReturn("text");

    ProbeResult result = d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.HTTP, xpath);

    assertThat(result)
        .isEqualTo(
            new ProbeResult(ARTICLE_URL, finalUrl, "Article Title", FullTextMethod.HTTP, "text"));
    verify(d.playwright, never()).renderPage(any(), anyBoolean());
  }

  @Test
  void articleHttpReadabilityWorksWhenPlaywrightIsDisabledAndConvertsNullText() throws Exception {
    Dependencies d = dependencies();
    when(d.http.get(ARTICLE_URL))
        .thenReturn(new HttpFetchService.FetchedResource(ARTICLE_URL, "<body>body</body>"));
    when(d.extractor.extract(any(), any(), eq(ExtractorKind.READABILITY), any())).thenReturn(null);

    ProbeResult result =
        d.service(false)
            .probeArticle(ARTICLE_URL, FullTextMethod.HTTP_READABILITY, Optional.of("//ignored"));

    assertThat(result.text()).isEmpty();
    verify(d.extractor)
        .extract(
            ARTICLE_URL.toString(),
            "<body>body</body>",
            ExtractorKind.READABILITY,
            Optional.of("//ignored"));
  }

  @Test
  void articleContinuesWhenTitleExtractionCannotParseNullHtml() throws Exception {
    Dependencies d = dependencies();
    when(d.http.get(ARTICLE_URL))
        .thenReturn(new HttpFetchService.FetchedResource(ARTICLE_URL, null));
    when(d.extractor.extract(
            ARTICLE_URL.toString(), null, ExtractorKind.XPATH_OR_BODY_TEXT, Optional.empty()))
        .thenReturn("text");

    ProbeResult result =
        d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.HTTP, Optional.empty());

    assertThat(result.title()).isNull();
    assertThat(result.text()).isEqualTo("text");
  }

  @Test
  void articleHttpFailuresPreserveCauseAndRestoreInterrupt() throws Exception {
    Dependencies io = dependencies();
    IOException failure = new IOException("network");
    when(io.http.get(ARTICLE_URL)).thenThrow(failure);
    assertThatThrownBy(
            () -> io.service(true).probeArticle(ARTICLE_URL, FullTextMethod.HTTP, Optional.empty()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("HTTP fetch failed for")
        .hasCause(failure);

    Dependencies interrupted = dependencies();
    InterruptedException failureInterrupt = new InterruptedException("stop");
    when(interrupted.http.get(ARTICLE_URL)).thenThrow(failureInterrupt);
    try {
      assertThatThrownBy(
              () ->
                  interrupted
                      .service(true)
                      .probeArticle(ARTICLE_URL, FullTextMethod.HTTP, Optional.empty()))
          .hasCause(failureInterrupt);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @MethodSource("playwrightMethods")
  void articlePlaywrightMethodsMapScrollAndExtractor(
      FullTextMethod method, boolean infy, ExtractorKind kind) throws Exception {
    Dependencies d = dependencies();
    URI finalUrl = URI.create("https://final/rendered");
    when(d.playwright.renderPage(ARTICLE_URL.toString(), infy))
        .thenReturn(new RenderedPage(finalUrl, "<body>rendered</body>"));
    when(d.extractor.extract(finalUrl.toString(), "<body>rendered</body>", kind, Optional.empty()))
        .thenReturn("text");

    ProbeResult result = d.service(true).probeArticle(ARTICLE_URL, method, Optional.empty());

    assertThat(result.finalUrl()).isEqualTo(finalUrl);
    verify(d.http, never()).get(any());
  }

  @Test
  void articlePlaywrightUsesInputUrlWhenRendererHasNoFinalUrl() {
    Dependencies d = dependencies();
    when(d.playwright.renderPage(ARTICLE_URL.toString(), false))
        .thenReturn(new RenderedPage(null, "html"));
    when(d.extractor.extract(
            ARTICLE_URL.toString(), "html", ExtractorKind.XPATH_OR_BODY_TEXT, Optional.empty()))
        .thenReturn("text");

    assertThat(
            d.service(true)
                .probeArticle(ARTICLE_URL, FullTextMethod.PLAYWRIGHT, Optional.empty())
                .finalUrl())
        .isEqualTo(ARTICLE_URL);
  }

  @Test
  void disabledOrMisconfiguredPlaywrightIsMappedWithoutExtraction() {
    Dependencies disabled = dependencies();
    assertThatThrownBy(
            () ->
                disabled
                    .service(false)
                    .probeArticle(ARTICLE_URL, FullTextMethod.PLAYWRIGHT, Optional.empty()))
        .isInstanceOf(FullTextProbeService.PlaywrightDisabledException.class);
    verify(disabled.playwright, never()).renderPage(any(), anyBoolean());

    Dependencies bad = dependencies();
    when(bad.playwright.renderPage(any(), anyBoolean()))
        .thenThrow(new IllegalStateException("not configured"));
    assertThatThrownBy(
            () ->
                bad.service(true)
                    .probeArticle(ARTICLE_URL, FullTextMethod.PLAYWRIGHT, Optional.empty()))
        .isInstanceOf(FullTextProbeService.PlaywrightDisabledException.class)
        .hasMessage("not configured");
  }

  @Test
  void feedNoMatchStopsBeforeAnyArticleOrFeedExtraction() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    when(d.documents.fetch(FEED_URL)).thenReturn(feed);
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                d.service(true)
                    .probeFeed(
                        FEED_URL,
                        FullTextMethod.HTTP,
                        FeedEntrySelection.first(),
                        Optional.empty()))
        .isInstanceOf(FullTextProbeService.NoMatchingEntryException.class)
        .hasMessageContaining(FEED_URL.toString());
    verify(d.http, never()).get(any());
    verify(d.playwright, never()).renderPage(any(), anyBoolean());
    verify(d.feedExtractor, never()).extract(any());
  }

  @Test
  void feedMethodUsesFeedTextAndDoesNotResolveEntryLink() {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "Entry title");
    when(d.documents.fetch(FEED_URL)).thenReturn(feed);
    when(d.picker.pick(feed, FeedEntrySelection.first(), false)).thenReturn(Optional.of(entry));
    when(d.feedExtractor.extract(entry)).thenReturn(Optional.of("feed text"));

    assertThat(
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.FEED, FeedEntrySelection.first(), Optional.empty()))
        .isEqualTo(
            new ProbeResult(FEED_URL, FEED_URL, "Entry title", FullTextMethod.FEED, "feed text"));
    verify(d.http, never()).resolveRedirect(any());
    verify(d.extractor, never()).extract(any(), any(), any(), any());
  }

  @Test
  void feedMethodRejectsXpathAndCanReturnEmptyText() {
    Dependencies xpath = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry(null, "title");
    when(xpath.documents.fetch(FEED_URL)).thenReturn(feed);
    when(xpath.picker.pick(feed, FeedEntrySelection.first(), false)).thenReturn(Optional.of(entry));
    assertThatThrownBy(
            () ->
                xpath
                    .service(true)
                    .probeFeed(
                        FEED_URL,
                        FullTextMethod.FEED,
                        FeedEntrySelection.first(),
                        Optional.of("//x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--xpath");
    verify(xpath.feedExtractor, never()).extract(any());

    Dependencies empty = dependencies();
    when(empty.documents.fetch(FEED_URL)).thenReturn(feed);
    when(empty.picker.pick(feed, FeedEntrySelection.first(), false)).thenReturn(Optional.of(entry));
    when(empty.feedExtractor.extract(entry)).thenReturn(Optional.empty());
    assertThat(
            empty
                .service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.FEED, FeedEntrySelection.first(), Optional.empty())
                .text())
        .isEmpty();
  }

  @Test
  void nonFeedRejectsNullAndBlankEntryLinks() throws Exception {
    for (String link : new String[] {null, " "}) {
      Dependencies d = dependencies();
      SyndFeed feed = mock(SyndFeed.class);
      SyndEntry entry = entry(link, "title");
      when(d.documents.fetch(FEED_URL)).thenReturn(feed);
      when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
      assertThatThrownBy(
              () ->
                  d.service(true)
                      .probeFeed(
                          FEED_URL,
                          FullTextMethod.HTTP,
                          FeedEntrySelection.first(),
                          Optional.empty()))
          .isInstanceOf(FullTextProbeService.NoMatchingEntryException.class)
          .hasMessageContaining("Selected entry has no link");
      verify(d.http, never()).get(any());
    }
  }

  @Test
  void nonFeedHttpUsesRedirectAndEntryTitleInPreferenceToHtmlTitle() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "Entry title");
    URI redirected = URI.create("https://redirected");
    URI finalUrl = URI.create("https://final");
    when(d.documents.fetch(FEED_URL)).thenReturn(feed);
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(URI.create("https://entry"))).thenReturn(redirected);
    when(d.http.get(redirected))
        .thenReturn(new HttpFetchService.FetchedResource(finalUrl, "<title>HTML title</title>"));
    when(d.extractor.extract(
            finalUrl.toString(),
            "<title>HTML title</title>",
            ExtractorKind.XPATH_OR_BODY_TEXT,
            Optional.empty()))
        .thenReturn("body");

    assertThat(
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.HTTP, FeedEntrySelection.first(), Optional.empty()))
        .isEqualTo(new ProbeResult(FEED_URL, finalUrl, "Entry title", FullTextMethod.HTTP, "body"));
  }

  @Test
  void nonFeedFallsBackAfterRedirectFailureAndHtmlTitleWhenEntryTitleIsMissing() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", null);
    URI link = URI.create("https://entry");
    when(d.documents.fetch(FEED_URL)).thenReturn(feed);
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link)).thenThrow(new IllegalArgumentException("bad redirect"));
    when(d.http.get(link))
        .thenReturn(new HttpFetchService.FetchedResource(link, "<title>HTML title</title>"));
    when(d.extractor.extract(any(), any(), eq(ExtractorKind.READABILITY), any())).thenReturn(null);

    ProbeResult result =
        d.service(true)
            .probeFeed(
                FEED_URL,
                FullTextMethod.HTTP_READABILITY,
                FeedEntrySelection.first(),
                Optional.empty());
    assertThat(result.title()).isEqualTo("HTML title");
    assertThat(result.text()).isEmpty();
  }

  @Test
  void nonFeedHttpFailuresWrapAndRestoreInterrupt() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "title");
    URI link = URI.create("https://entry");
    when(d.documents.fetch(FEED_URL)).thenReturn(feed);
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link)).thenReturn(link);
    IOException failure = new IOException("network");
    when(d.http.get(link)).thenThrow(failure, new InterruptedException("stop"));
    assertThatThrownBy(
            () ->
                d.service(true)
                    .probeFeed(
                        FEED_URL,
                        FullTextMethod.HTTP,
                        FeedEntrySelection.first(),
                        Optional.empty()))
        .hasMessageContaining("HTTP fetch failed for entry")
        .hasCause(failure);

    try {
      assertThatThrownBy(
              () ->
                  d.service(true)
                      .probeFeed(
                          FEED_URL,
                          FullTextMethod.HTTP,
                          FeedEntrySelection.first(),
                          Optional.empty()))
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @MethodSource("feedPlaywrightMethods")
  void nonFeedPlaywrightMapsScrollReadabilityAndFinalUrlFallback(
      FullTextMethod method, boolean infy, ExtractorKind kind) {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "title");
    URI link = URI.create("https://entry");
    when(d.documents.fetch(FEED_URL)).thenReturn(feed);
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link)).thenReturn(link);
    when(d.playwright.renderPage(link.toString(), infy)).thenReturn(new RenderedPage(null, "html"));
    when(d.extractor.extract(link.toString(), "html", kind, Optional.empty())).thenReturn("text");

    assertThat(
            d.service(true)
                .probeFeed(FEED_URL, method, FeedEntrySelection.first(), Optional.empty())
                .finalUrl())
        .isEqualTo(link);
  }

  @Test
  void nonFeedPlaywrightUsesRendererFinalUrl() {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "title");
    URI link = URI.create("https://entry");
    URI finalUrl = URI.create("https://final");
    when(d.documents.fetch(FEED_URL)).thenReturn(feed);
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link)).thenReturn(link);
    when(d.playwright.renderPage(link.toString(), false))
        .thenReturn(new RenderedPage(finalUrl, "html"));
    when(d.extractor.extract(
            finalUrl.toString(), "html", ExtractorKind.XPATH_OR_BODY_TEXT, Optional.empty()))
        .thenReturn("text");

    assertThat(
            d.service(true)
                .probeFeed(
                    FEED_URL,
                    FullTextMethod.PLAYWRIGHT,
                    FeedEntrySelection.first(),
                    Optional.empty())
                .finalUrl())
        .isEqualTo(finalUrl);
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> playwrightMethods() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            FullTextMethod.PLAYWRIGHT, false, ExtractorKind.XPATH_OR_BODY_TEXT),
        org.junit.jupiter.params.provider.Arguments.of(
            FullTextMethod.PLAYWRIGHT_READABILITY, false, ExtractorKind.READABILITY),
        org.junit.jupiter.params.provider.Arguments.of(
            FullTextMethod.PLAYWRIGHT_INFY_SCROLL, true, ExtractorKind.XPATH_OR_BODY_TEXT),
        org.junit.jupiter.params.provider.Arguments.of(
            FullTextMethod.PLAYWRIGHT_INFY_SCROLL_READABILITY, true, ExtractorKind.READABILITY));
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> feedPlaywrightMethods() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            FullTextMethod.PLAYWRIGHT, false, ExtractorKind.XPATH_OR_BODY_TEXT),
        org.junit.jupiter.params.provider.Arguments.of(
            FullTextMethod.PLAYWRIGHT_INFY_SCROLL, true, ExtractorKind.XPATH_OR_BODY_TEXT),
        org.junit.jupiter.params.provider.Arguments.of(
            FullTextMethod.PLAYWRIGHT_READABILITY, false, ExtractorKind.READABILITY));
  }

  private SyndEntry entry(String link, String title) {
    SyndEntry entry = mock(SyndEntry.class);
    when(entry.getLink()).thenReturn(link);
    when(entry.getTitle()).thenReturn(title);
    return entry;
  }

  private Dependencies dependencies() {
    return new Dependencies();
  }

  private static FeedReaderProperties properties(boolean playwrightEnabled) {
    return new FeedReaderProperties(
        null,
        null,
        new FeedReaderProperties.Http("ua", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            playwrightEnabled,
            true,
            10,
            10,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            null,
            null,
            1,
            1,
            Duration.ofMillis(10)),
        null,
        java.util.List.of());
  }

  private static class Dependencies {
    private final HttpFetchService http = mock(HttpFetchService.class);
    private final PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    private final HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    private final FeedDocumentService documents = mock(FeedDocumentService.class);
    private final FeedEntryPicker picker = mock(FeedEntryPicker.class);
    private final FeedEntryFullTextExtractor feedExtractor = mock(FeedEntryFullTextExtractor.class);

    private FullTextProbeService service(boolean playwrightEnabled) {
      return new FullTextProbeService(
          http,
          playwright,
          extractor,
          documents,
          picker,
          feedExtractor,
          properties(playwrightEnabled));
    }
  }
}
