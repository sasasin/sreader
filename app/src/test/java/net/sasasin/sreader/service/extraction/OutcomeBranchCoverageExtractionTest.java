package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.dankito.readability4j.Article;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.outcome.BatchStopReason;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Extra branch coverage for P0-3 outcome paths (extraction). */
class OutcomeBranchCoverageExtractionTest {

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void fullTextDefaultExtractUsesHttpMethod() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentHeader header =
        new ContentHeader("id", "feed", "https://s", "https://f", "https://c", "t", null, null);
    when(http.get(URI.create("https://f")))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://f"), "<html/>"));
    when(extractor.extract(any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "body", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service(http, extractor, mock(PlaywrightHtmlSource.class), true).extract(header);
    assertThat(extracted.text()).isEqualTo("body");
  }

  @Test
  void fullTextHttpStatusFailureMapsKind() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeader header =
        new ContentHeader("id", "feed", "https://s", "https://f", "https://c", "t", null, null);
    when(http.get(URI.create("https://f")))
        .thenThrow(new IOException("GET https://f returned HTTP 404"));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service(http, mock(HtmlTextExtractor.class), mock(PlaywrightHtmlSource.class), true)
                .extract(header, FullTextMethod.HTTP);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.HTTP_STATUS);
  }

  @Test
  void fullTextFeedBlankAfterJsoupIsNoContent() {
    ContentHeader header =
        new ContentHeader(
            "id", "feed", "https://s", "https://f", "https://c", "t", null, "<p>   </p>");
    TextExtractionOutcome.NoContent noContent =
        (TextExtractionOutcome.NoContent)
            service(
                    mock(HttpFetchService.class),
                    mock(HtmlTextExtractor.class),
                    mock(PlaywrightHtmlSource.class),
                    true)
                .extract(header, FullTextMethod.FEED);
    assertThat(noContent.reason()).isEqualTo(NoContentReason.FEED_CONTENT_MISSING);
  }

  @Test
  void batchWriterNoContentAndPersistenceFailureAndUnexpected() throws Exception {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);

    ContentHeader noContentWrite =
        new ContentHeader("1", "f", "https://a", "https://a", "https://a", "t", null, null);
    ContentHeader persistFail =
        new ContentHeader("2", "f", "https://b", "https://b", "https://b", "t", null, null);
    ContentHeader unexpected =
        new ContentHeader("3", "f", "https://c", "https://c", "https://c", "t", null, null);

    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(noContentWrite, FullTextMethod.HTTP),
                new PendingFullTextTarget(persistFail, FullTextMethod.HTTP),
                new PendingFullTextTarget(unexpected, FullTextMethod.HTTP)));
    when(http.get(any()))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://a"), "h"))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://b"), "h"))
        .thenThrow(new RuntimeException("boom unexpected path"));
    when(extractor.extract(any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "one", ExtractionDecision.of(ExtractionSource.BODY_TEXT)))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "two", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));
    when(writer.saveIfAbsent(eq(noContentWrite), eq("one")))
        .thenReturn(ContentFullTextWriteOutcome.NO_CONTENT);
    when(writer.saveIfAbsent(eq(persistFail), eq("two")))
        .thenThrow(new RuntimeException("db write failed"));

    FullTextExtractionBatchResult result =
        new FullTextExtractionService(
                repository,
                writer,
                extractor,
                http,
                mock(PlaywrightHtmlSource.class),
                properties(true))
            .extractPending(10);

    assertThat(result.noContent()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(2);
    assertThat(result.selectedTargets()).isEqualTo(3);
  }

  @Test
  void batchLogsExtractionFailureWithoutCause() {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentHeader header =
        new ContentHeader("1", "f", "https://a", "https://a", "https://a", "t", null, null);
    when(repository.findWithoutFullTextForUrlExtraction(5))
        .thenReturn(List.of(new PendingFullTextTarget(header, FullTextMethod.HTTP)));
    HttpFetchService http = mock(HttpFetchService.class);
    // Force extract path that returns Failed without cause via mocked extractor after HTTP ok.
    try {
      when(http.get(any()))
          .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://a"), "h"));
    } catch (Exception ignored) {
      // mock setup
    }
    when(extractor.extract(any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Failed(
                OperationFailure.of(
                    FailureStage.EXTRACT_TEXT,
                    FailureKind.EXTRACTION,
                    "https://a",
                    "empty extract without cause")));

    FullTextExtractionBatchResult result =
        new FullTextExtractionService(
                repository,
                writer,
                extractor,
                http,
                mock(PlaywrightHtmlSource.class),
                properties(true))
            .extractPending(5);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.stopReason()).isEmpty();
  }

  @Test
  void batchStopsWhenThreadAlreadyInterrupted() {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentHeader header =
        new ContentHeader("1", "f", "https://a", "https://a", "https://a", "t", null, null);
    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(List.of(new PendingFullTextTarget(header, FullTextMethod.HTTP)));
    Thread.currentThread().interrupt();

    FullTextExtractionBatchResult result =
        new FullTextExtractionService(
                repository,
                mock(ContentFullTextWriter.class),
                mock(HtmlTextExtractor.class),
                mock(HttpFetchService.class),
                mock(PlaywrightHtmlSource.class),
                properties(true))
            .extractPending(10);

    assertThat(result.stopReason()).contains(BatchStopReason.INTERRUPTED);
    assertThat(result.failed() + result.inserted() + result.noContent()).isZero();
  }

  @Test
  void htmlReadabilityEmptyBodyIsNoContentWithFallback() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    Article article = mock(Article.class);
    when(article.getTextContent()).thenReturn("");
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules, (url, html) -> article);
    TextExtractionOutcome.NoContent noContent =
        (TextExtractionOutcome.NoContent)
            extractor.extract(
                "https://example.test/a",
                "<html><body></body></html>",
                net.sasasin.sreader.domain.ExtractionPlan.ExtractorKind.READABILITY);
    assertThat(noContent.reason()).isEqualTo(NoContentReason.BODY_TEXT_EMPTY);
    assertThat(noContent.decision().fallbackReason())
        .contains(ExtractionFallbackReason.READABILITY_EMPTY);
  }

  @Test
  void htmlExtractorConfiguredXpathBodyEmptyAfterFallback() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/a";
    when(rules.findBestRule(url))
        .thenReturn(
            Optional.of(new net.sasasin.sreader.domain.ExtractRule("id", url, "//article")));
    TextExtractionOutcome.NoContent noContent =
        (TextExtractionOutcome.NoContent)
            extractor.extract(
                url,
                "<html><body>   </body></html>",
                net.sasasin.sreader.domain.ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT);
    assertThat(noContent.reason()).isEqualTo(NoContentReason.BODY_TEXT_EMPTY);
    assertThat(noContent.decision().fallbackReason())
        .contains(ExtractionFallbackReason.CONFIGURED_XPATH_NO_MATCH);
  }

  @Test
  void feedEntryFullTextExtractorDetectsHtmlByMarkupHeuristic() {
    FeedEntryFullTextExtractor extractor = new FeedEntryFullTextExtractor();
    SyndEntryImpl entry = new SyndEntryImpl();
    SyndContentImpl content = new SyndContentImpl();
    content.setType("text/plain");
    content.setValue("<div>Heuristic <b>html</b></div>");
    entry.getContents().add(content);
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted) extractor.extract(entry);
    assertThat(extracted.text()).isEqualTo("Heuristic html");
  }

  private FullTextExtractionService service(
      HttpFetchService http,
      HtmlTextExtractor extractor,
      PlaywrightHtmlSource playwright,
      boolean playwrightEnabled) {
    return new FullTextExtractionService(
        mock(ContentHeaderRepository.class),
        mock(ContentFullTextWriter.class),
        extractor,
        http,
        playwright,
        properties(playwrightEnabled));
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
