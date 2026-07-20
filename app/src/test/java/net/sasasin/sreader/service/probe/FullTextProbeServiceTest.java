package net.sasasin.sreader.service.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionSource;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.extraction.HtmlTextExtractor;
import net.sasasin.sreader.service.extraction.NoContentReason;
import net.sasasin.sreader.service.extraction.TextExtractionOutcome;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.PlaywrightRenderMode;
import net.sasasin.sreader.service.extraction.browser.RenderedPage;
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentOutcome;
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentService;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.RedirectResolution;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FullTextProbeServiceTest {

  private static final URI ARTICLE_URL = URI.create("https://input/article");
  private static final URI FEED_URL = URI.create("https://input/feed");

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void articleFeedMethodIsInvalidRequestWithoutDependencies() throws Exception {
    Dependencies d = dependencies();
    ProbeOutcome outcome =
        d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.FEED, Optional.empty());
    assertThat(outcome).isInstanceOf(ProbeOutcome.InvalidRequest.class);
    verify(d.http, never()).get(any());
    verify(d.playwright, never()).renderPage(any(), any());
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
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "text", ExtractionDecision.of(ExtractionSource.XPATH_OVERRIDE)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.HTTP, xpath);

    assertThat(result.document().inputUrl()).isEqualTo(ARTICLE_URL);
    assertThat(result.document().finalUrl()).isEqualTo(finalUrl);
    assertThat(result.document().title()).contains("Article Title");
    assertThat(result.text()).isEqualTo("text");
    verify(d.playwright, never()).renderPage(any(), any());
  }

  @Test
  void articleHttpReadabilityWorksWhenPlaywrightIsDisabledAndMapsNoContent() throws Exception {
    Dependencies d = dependencies();
    when(d.http.get(ARTICLE_URL))
        .thenReturn(new HttpFetchService.FetchedResource(ARTICLE_URL, "<body>body</body>"));
    when(d.extractor.extract(any(), any(), eq(ExtractorKind.READABILITY), any()))
        .thenReturn(
            new TextExtractionOutcome.NoContent(
                NoContentReason.BODY_TEXT_EMPTY,
                ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.NoContent result =
        (ProbeOutcome.NoContent)
            d.service(false)
                .probeArticle(
                    ARTICLE_URL, FullTextMethod.HTTP_READABILITY, Optional.of("//ignored"));

    assertThat(result.reason()).isEqualTo(NoContentReason.BODY_TEXT_EMPTY);
    verify(d.extractor)
        .extract(
            ARTICLE_URL.toString(),
            "<body>body</body>",
            ExtractorKind.READABILITY,
            Optional.of("//ignored"));
  }

  @Test
  void articleContinuesWhenTitleExtractionCannotParseHtml() throws Exception {
    Dependencies d = dependencies();
    when(d.http.get(ARTICLE_URL)).thenReturn(new HttpFetchService.FetchedResource(ARTICLE_URL, ""));
    when(d.extractor.extract(
            ARTICLE_URL.toString(), "", ExtractorKind.XPATH_OR_BODY_TEXT, Optional.empty()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "text", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.HTTP, Optional.empty());

    assertThat(result.document().title()).isEmpty();
    assertThat(result.text()).isEqualTo("text");
  }

  @Test
  void articleHttpFailuresAreFailedAndRestoreInterrupt() throws Exception {
    Dependencies io = dependencies();
    IOException failure = new IOException("network");
    when(io.http.get(ARTICLE_URL)).thenThrow(failure);
    ProbeOutcome.Failed failed =
        (ProbeOutcome.Failed)
            io.service(true).probeArticle(ARTICLE_URL, FullTextMethod.HTTP, Optional.empty());
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_ARTICLE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.IO);
    assertThat(failed.failure().cause()).contains(failure);

    Dependencies interrupted = dependencies();
    InterruptedException failureInterrupt = new InterruptedException("stop");
    when(interrupted.http.get(ARTICLE_URL)).thenThrow(failureInterrupt);
    ProbeOutcome.Failed interruptedFailed =
        (ProbeOutcome.Failed)
            interrupted
                .service(true)
                .probeArticle(ARTICLE_URL, FullTextMethod.HTTP, Optional.empty());
    assertThat(interruptedFailed.failure().kind()).isEqualTo(FailureKind.INTERRUPTED);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("playwrightMethods")
  void articlePlaywrightMethodsMapScrollAndExtractor(
      FullTextMethod method, boolean infy, ExtractorKind kind) throws Exception {
    Dependencies d = dependencies();
    URI finalUrl = URI.create("https://final/rendered");
    when(d.playwright.renderPage(
            eq(ARTICLE_URL),
            eq(infy ? PlaywrightRenderMode.INFY_SCROLL : PlaywrightRenderMode.STANDARD)))
        .thenReturn(new RenderedPage(finalUrl, "<body>rendered</body>"));
    when(d.extractor.extract(finalUrl.toString(), "<body>rendered</body>", kind, Optional.empty()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "text", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true).probeArticle(ARTICLE_URL, method, Optional.empty());

    assertThat(result.document().finalUrl()).isEqualTo(finalUrl);
    verify(d.http, never()).get(any());
  }

  @Test
  void articlePlaywrightUsesInputUrlWhenRendererHasNoFinalUrl() {
    Dependencies d = dependencies();
    when(d.playwright.renderPage(ARTICLE_URL, PlaywrightRenderMode.STANDARD))
        .thenReturn(new RenderedPage(ARTICLE_URL, "html"));
    when(d.extractor.extract(
            ARTICLE_URL.toString(), "html", ExtractorKind.XPATH_OR_BODY_TEXT, Optional.empty()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "text", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.PLAYWRIGHT, Optional.empty());
    assertThat(result.document().finalUrl()).isEqualTo(ARTICLE_URL);
  }

  @Test
  void disabledPlaywrightIsSkippedWithoutExtraction() {
    Dependencies disabled = dependencies();
    ProbeOutcome.Skipped skipped =
        (ProbeOutcome.Skipped)
            disabled
                .service(false)
                .probeArticle(ARTICLE_URL, FullTextMethod.PLAYWRIGHT, Optional.empty());
    assertThat(skipped.reason()).isEqualTo(ProbeSkipReason.PLAYWRIGHT_DISABLED);
    verify(disabled.playwright, never()).renderPage(any(), any());
  }

  @Test
  void playwrightRenderFailureIsFailedRender() {
    Dependencies bad = dependencies();
    when(bad.playwright.renderPage(any(), any()))
        .thenThrow(new IllegalStateException("not configured"));
    ProbeOutcome.Failed failed =
        (ProbeOutcome.Failed)
            bad.service(true)
                .probeArticle(ARTICLE_URL, FullTextMethod.PLAYWRIGHT, Optional.empty());
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.RENDER_ARTICLE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.RENDER);
  }

  @Test
  void feedNoMatchStopsBeforeAnyArticleOrFeedExtraction() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.empty());

    ProbeOutcome.NoMatchingEntry noMatch =
        (ProbeOutcome.NoMatchingEntry)
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.HTTP, FeedEntrySelection.first(), Optional.empty());
    assertThat(noMatch.message()).contains(FEED_URL.toString());
    verify(d.http, never()).get(any());
    verify(d.playwright, never()).renderPage(any(), any());
    verify(d.feedExtractor, never()).extract(any());
  }

  @Test
  void feedMethodUsesFeedTextAndDoesNotResolveEntryLink() {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "Entry title");
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), false)).thenReturn(Optional.of(entry));
    when(d.feedExtractor.extract(entry))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "feed text", ExtractionDecision.of(ExtractionSource.FEED)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.FEED, FeedEntrySelection.first(), Optional.empty());
    assertThat(result.text()).isEqualTo("feed text");
    assertThat(result.document().title()).contains("Entry title");
    assertThat(result.document().finalUrl()).isEqualTo(FEED_URL);
    verify(d.http, never()).resolveRedirect(any());
    verify(d.extractor, never()).extract(any(), any(), any(), any());
  }

  @Test
  void feedMethodRejectsXpathAndCanReturnNoContent() {
    Dependencies xpath = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry(null, "title");
    when(xpath.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(xpath.picker.pick(feed, FeedEntrySelection.first(), false)).thenReturn(Optional.of(entry));
    ProbeOutcome.InvalidRequest invalid =
        (ProbeOutcome.InvalidRequest)
            xpath
                .service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.FEED, FeedEntrySelection.first(), Optional.of("//x"));
    assertThat(invalid.message()).contains("--xpath");
    verify(xpath.feedExtractor, never()).extract(any());

    Dependencies empty = dependencies();
    when(empty.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(empty.picker.pick(feed, FeedEntrySelection.first(), false)).thenReturn(Optional.of(entry));
    when(empty.feedExtractor.extract(entry))
        .thenReturn(
            new TextExtractionOutcome.NoContent(
                NoContentReason.FEED_CONTENT_MISSING,
                ExtractionDecision.of(ExtractionSource.FEED)));
    ProbeOutcome.NoContent noContent =
        (ProbeOutcome.NoContent)
            empty
                .service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.FEED, FeedEntrySelection.first(), Optional.empty());
    assertThat(noContent.reason()).isEqualTo(NoContentReason.FEED_CONTENT_MISSING);
  }

  @Test
  void nonFeedRejectsNullAndBlankEntryLinks() throws Exception {
    for (String link : new String[] {null, " "}) {
      Dependencies d = dependencies();
      SyndFeed feed = mock(SyndFeed.class);
      SyndEntry entry = entry(link, "title");
      when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
      when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
      ProbeOutcome.NoMatchingEntry noMatch =
          (ProbeOutcome.NoMatchingEntry)
              d.service(true)
                  .probeFeed(
                      FEED_URL, FullTextMethod.HTTP, FeedEntrySelection.first(), Optional.empty());
      assertThat(noMatch.message()).contains("Selected entry has no link");
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
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(URI.create("https://entry")))
        .thenReturn(new RedirectResolution.Resolved(URI.create("https://entry"), redirected));
    when(d.http.get(redirected))
        .thenReturn(new HttpFetchService.FetchedResource(finalUrl, "<title>HTML title</title>"));
    when(d.extractor.extract(
            finalUrl.toString(),
            "<title>HTML title</title>",
            ExtractorKind.XPATH_OR_BODY_TEXT,
            Optional.empty()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "body", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.HTTP, FeedEntrySelection.first(), Optional.empty());
    assertThat(result.document().finalUrl()).isEqualTo(finalUrl);
    assertThat(result.document().title()).contains("Entry title");
    assertThat(result.text()).isEqualTo("body");
  }

  @Test
  void nonFeedContinuesAfterNonInterruptedRedirectFallback() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", null);
    URI link = URI.create("https://entry");
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link))
        .thenReturn(
            new RedirectResolution.Fallback(
                link,
                OperationFailure.of(
                    FailureStage.RESOLVE_REDIRECT,
                    FailureKind.INVALID_INPUT,
                    link.toString(),
                    "bad redirect")));
    when(d.http.get(link))
        .thenReturn(new HttpFetchService.FetchedResource(link, "<title>HTML title</title>"));
    when(d.extractor.extract(any(), any(), eq(ExtractorKind.READABILITY), any()))
        .thenReturn(
            new TextExtractionOutcome.NoContent(
                NoContentReason.BODY_TEXT_EMPTY,
                ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.NoContent result =
        (ProbeOutcome.NoContent)
            d.service(true)
                .probeFeed(
                    FEED_URL,
                    FullTextMethod.HTTP_READABILITY,
                    FeedEntrySelection.first(),
                    Optional.empty());
    assertThat(result.document().title()).contains("HTML title");
    assertThat(result.reason()).isEqualTo(NoContentReason.BODY_TEXT_EMPTY);
  }

  @Test
  void nonFeedRedirectInterruptedIsFailed() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "title");
    URI link = URI.create("https://entry");
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link))
        .thenReturn(
            new RedirectResolution.Fallback(
                link,
                OperationFailure.of(
                    FailureStage.RESOLVE_REDIRECT,
                    FailureKind.INTERRUPTED,
                    link.toString(),
                    "interrupted")));

    ProbeOutcome.Failed failed =
        (ProbeOutcome.Failed)
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.HTTP, FeedEntrySelection.first(), Optional.empty());
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.INTERRUPTED);
    verify(d.http, never()).get(any());
  }

  @Test
  void nonFeedHttpFailuresAreFailedAndRestoreInterrupt() throws Exception {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "title");
    URI link = URI.create("https://entry");
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link)).thenReturn(new RedirectResolution.Resolved(link, link));
    IOException failure = new IOException("network");
    when(d.http.get(link)).thenThrow(failure, new InterruptedException("stop"));

    ProbeOutcome.Failed ioFailed =
        (ProbeOutcome.Failed)
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.HTTP, FeedEntrySelection.first(), Optional.empty());
    assertThat(ioFailed.failure().message()).contains("HTTP fetch failed for entry");
    assertThat(ioFailed.failure().cause()).contains(failure);

    ProbeOutcome.Failed interruptedFailed =
        (ProbeOutcome.Failed)
            d.service(true)
                .probeFeed(
                    FEED_URL, FullTextMethod.HTTP, FeedEntrySelection.first(), Optional.empty());
    assertThat(interruptedFailed.failure().kind()).isEqualTo(FailureKind.INTERRUPTED);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("feedPlaywrightMethods")
  void nonFeedPlaywrightMapsScrollReadabilityAndFinalUrlFallback(
      FullTextMethod method, boolean infy, ExtractorKind kind) {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "title");
    URI link = URI.create("https://entry");
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link)).thenReturn(new RedirectResolution.Resolved(link, link));
    when(d.playwright.renderPage(
            eq(link), eq(infy ? PlaywrightRenderMode.INFY_SCROLL : PlaywrightRenderMode.STANDARD)))
        .thenReturn(new RenderedPage(link, "html"));
    when(d.extractor.extract(link.toString(), "html", kind, Optional.empty()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "text", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true)
                .probeFeed(FEED_URL, method, FeedEntrySelection.first(), Optional.empty());
    assertThat(result.document().finalUrl()).isEqualTo(link);
  }

  @Test
  void nonFeedPlaywrightUsesRendererFinalUrl() {
    Dependencies d = dependencies();
    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = entry("https://entry", "title");
    URI link = URI.create("https://entry");
    URI finalUrl = URI.create("https://final");
    when(d.documents.fetch(FEED_URL)).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(d.picker.pick(feed, FeedEntrySelection.first(), true)).thenReturn(Optional.of(entry));
    when(d.http.resolveRedirect(link)).thenReturn(new RedirectResolution.Resolved(link, link));
    when(d.playwright.renderPage(link, PlaywrightRenderMode.STANDARD))
        .thenReturn(new RenderedPage(finalUrl, "html"));
    when(d.extractor.extract(
            finalUrl.toString(), "html", ExtractorKind.XPATH_OR_BODY_TEXT, Optional.empty()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "text", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            d.service(true)
                .probeFeed(
                    FEED_URL,
                    FullTextMethod.PLAYWRIGHT,
                    FeedEntrySelection.first(),
                    Optional.empty());
    assertThat(result.document().finalUrl()).isEqualTo(finalUrl);
  }

  @Test
  void invalidExplicitXpathMapsToInvalidRequest() throws Exception {
    Dependencies d = dependencies();
    when(d.http.get(ARTICLE_URL))
        .thenReturn(new HttpFetchService.FetchedResource(ARTICLE_URL, "<html/>"));
    when(d.extractor.extract(any(), any(), any(), eq(Optional.of("///bad["))))
        .thenReturn(
            new TextExtractionOutcome.Failed(
                OperationFailure.of(
                    FailureStage.EXTRACT_TEXT,
                    FailureKind.INVALID_INPUT,
                    ARTICLE_URL.toString(),
                    "Invalid explicit XPath")));

    ProbeOutcome outcome =
        d.service(true).probeArticle(ARTICLE_URL, FullTextMethod.HTTP, Optional.of("///bad["));
    assertThat(outcome).isInstanceOf(ProbeOutcome.InvalidRequest.class);
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
          new ProbeDocumentFetcher(http, playwright, properties(playwrightEnabled)),
          extractor,
          documents,
          picker,
          feedExtractor);
    }
  }
}
