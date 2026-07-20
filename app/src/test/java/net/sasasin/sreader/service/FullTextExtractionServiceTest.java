package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.ExtractionPlan;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FullTextExtractionServiceTest {

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void fetchesUsingFetchUrlAndUsesFinalResponseUriAsExtractorBaseUrl() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    URI finalUri = URI.create("https://final.test/article");
    when(http.get(URI.create(header.fetchUrl())))
        .thenReturn(new HttpFetchService.FetchedResource(finalUri, "<html>content</html>"));
    when(extractor.extract(
            eq(finalUri.toString()),
            eq("<html>content</html>"),
            any(ExtractionPlan.ExtractorKind.class)))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "text", ExtractionDecision.of(ExtractionSource.READABILITY)));
    FullTextExtractionService service = service(http, extractor, mock(PlaywrightHtmlSource.class));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted) service.extract(header, FullTextMethod.HTTP_READABILITY);
    assertThat(extracted.text()).isEqualTo("text");
    verify(http).get(URI.create(header.fetchUrl()));
    verify(http, never()).get(URI.create(header.sourceUrl()));
    verify(http, never()).get(URI.create(header.canonicalUrl()));
    verify(extractor)
        .extract(
            finalUri.toString(), "<html>content</html>", ExtractionPlan.ExtractorKind.READABILITY);
  }

  @Test
  void fetchesAndExtractsRenderedHtmlUsingFetchUrl() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    when(playwright.render(header.fetchUrl(), false))
        .thenReturn("<html><body><main>Rendered body</main></body></html>");
    when(rules.findBestRule(header.fetchUrl())).thenReturn(Optional.empty());
    FullTextExtractionService service = service(http, extractor, playwright);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted) service.extract(header, FullTextMethod.PLAYWRIGHT);
    assertThat(extracted.text()).isEqualTo("Rendered body");
    verify(playwright).render(header.fetchUrl(), false);
    verify(playwright, never()).render(header.sourceUrl(), false);
    verify(playwright, never()).render(header.canonicalUrl(), false);
    verify(http, never()).get(any());
  }

  @Test
  void extractsFeedBodyWithoutNetworkAccess() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeader header =
        new ContentHeader(
            "id",
            "feed",
            "https://source.test/article",
            "https://fetch.test/article",
            "https://canonical.test/article",
            "title",
            null,
            "<p>Feed body</p>");

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service(http, mock(HtmlTextExtractor.class), mock(PlaywrightHtmlSource.class))
                .extract(header, FullTextMethod.FEED);
    assertThat(extracted.text()).isEqualTo("Feed body");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.FEED);
    verify(http, never()).get(any());
  }

  @Test
  void feedMissingContentIsNoContent() {
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    TextExtractionOutcome.NoContent noContent =
        (TextExtractionOutcome.NoContent)
            service(
                    mock(HttpFetchService.class),
                    mock(HtmlTextExtractor.class),
                    mock(PlaywrightHtmlSource.class))
                .extract(header, FullTextMethod.FEED);
    assertThat(noContent.reason()).isEqualTo(NoContentReason.FEED_CONTENT_MISSING);
  }

  @Test
  void httpIoFailureIsFailed() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    when(http.get(URI.create(header.fetchUrl()))).thenThrow(new IOException("network"));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service(http, mock(HtmlTextExtractor.class), mock(PlaywrightHtmlSource.class))
                .extract(header, FullTextMethod.HTTP);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_ARTICLE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.IO);
  }

  @Test
  void httpInterruptionIsFailedAndSetsFlag() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    when(http.get(URI.create(header.fetchUrl()))).thenThrow(new InterruptedException("stop"));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service(http, mock(HtmlTextExtractor.class), mock(PlaywrightHtmlSource.class))
                .extract(header, FullTextMethod.HTTP);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.INTERRUPTED);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void playwrightDisabledIsSkipped() {
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            mock(HttpFetchService.class),
            mock(PlaywrightHtmlSource.class),
            properties(false));

    TextExtractionOutcome.Skipped skipped =
        (TextExtractionOutcome.Skipped) service.extract(header, FullTextMethod.PLAYWRIGHT);
    assertThat(skipped.reason()).isEqualTo(TextExtractionSkipReason.PLAYWRIGHT_DISABLED);
  }

  @Test
  void playwrightRenderFailureIsFailed() {
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    when(playwright.render(header.fetchUrl(), false))
        .thenThrow(new RuntimeException("render boom"));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service(mock(HttpFetchService.class), mock(HtmlTextExtractor.class), playwright)
                .extract(header, FullTextMethod.PLAYWRIGHT);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.RENDER_ARTICLE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.RENDER);
  }

  @Test
  void skipsDisabledPlaywrightPendingTargetInBatch() {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(List.of(new PendingFullTextTarget(header, FullTextMethod.PLAYWRIGHT)));
    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            mock(HtmlTextExtractor.class),
            mock(HttpFetchService.class),
            mock(PlaywrightHtmlSource.class),
            properties(false));

    FullTextExtractionBatchResult result = service.extractPending(10);
    assertThat(result.selectedTargets()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.inserted()).isZero();
    assertThat(result.failed()).isZero();
    verify(writer, never()).saveIfAbsent(any(), any());
  }

  @Test
  void batchCountsInsertedAlreadyPresentNoContentAndFailures() throws Exception {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);

    ContentHeader insertedHeader = header("https://a", "https://a");
    ContentHeader existingHeader =
        new ContentHeader("id2", "feed", "https://b", "https://b", "https://b", "t", null, null);
    ContentHeader emptyHeader =
        new ContentHeader("id3", "feed", "https://c", "https://c", "https://c", "t", null, null);
    ContentHeader failedHeader =
        new ContentHeader("id4", "feed", "https://d", "https://d", "https://d", "t", null, null);

    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(insertedHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(existingHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(emptyHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(failedHeader, FullTextMethod.HTTP)));

    when(http.get(any()))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://a"), "<html/>"))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://b"), "<html/>"))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://c"), "<html/>"))
        .thenThrow(new IOException("boom"));

    when(extractor.extract(any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "one", ExtractionDecision.of(ExtractionSource.BODY_TEXT)))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "two", ExtractionDecision.of(ExtractionSource.BODY_TEXT)))
        .thenReturn(
            new TextExtractionOutcome.NoContent(
                NoContentReason.BODY_TEXT_EMPTY,
                ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    when(writer.saveIfAbsent(eq(insertedHeader), eq("one")))
        .thenReturn(ContentFullTextWriteOutcome.INSERTED);
    when(writer.saveIfAbsent(eq(existingHeader), eq("two")))
        .thenReturn(ContentFullTextWriteOutcome.ALREADY_EXISTS);

    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            extractor,
            http,
            mock(PlaywrightHtmlSource.class),
            properties(true));

    FullTextExtractionBatchResult result = service.extractPending(10);
    assertThat(result.selectedTargets()).isEqualTo(4);
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.alreadyPresent()).isEqualTo(1);
    assertThat(result.noContent()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.skipped()).isZero();
    assertThat(result.stopReason()).isEmpty();
  }

  @Test
  void batchStopsOnInterruptedExtraction() throws Exception {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);

    ContentHeader first =
        new ContentHeader("id1", "feed", "https://a", "https://a", "https://a", "t", null, null);
    ContentHeader second =
        new ContentHeader("id2", "feed", "https://b", "https://b", "https://b", "t", null, null);
    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(first, FullTextMethod.HTTP),
                new PendingFullTextTarget(second, FullTextMethod.HTTP)));
    when(http.get(URI.create("https://a"))).thenThrow(new InterruptedException("stop"));

    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            extractor,
            http,
            mock(PlaywrightHtmlSource.class),
            properties(true));

    FullTextExtractionBatchResult result = service.extractPending(10);
    assertThat(result.selectedTargets()).isEqualTo(2);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.stopReason()).contains(BatchStopReason.INTERRUPTED);
    verify(http, times(1)).get(any());
    verify(writer, never()).saveIfAbsent(any(), any());
  }

  private ContentHeader header(String sourceUrl, String fetchUrl) {
    return new ContentHeader(
        "id", "feed", sourceUrl, fetchUrl, "https://canonical.test/article", "title", null, null);
  }

  private FullTextExtractionService service(
      HttpFetchService http, HtmlTextExtractor extractor, PlaywrightHtmlSource playwright) {
    return new FullTextExtractionService(
        mock(ContentHeaderRepository.class),
        mock(ContentFullTextWriter.class),
        extractor,
        http,
        playwright,
        properties(true));
  }

  private FeedReaderProperties properties(boolean playwrightEnabled) {
    return new FeedReaderProperties(
        new FeedReaderProperties.Scheduler(false, "0 */15 * * * *"),
        new FeedReaderProperties.Job(false),
        new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            playwrightEnabled,
            true,
            1280,
            1600,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            null,
            null,
            1,
            1,
            Duration.ofMillis(1)),
        null,
        List.of());
  }
}
