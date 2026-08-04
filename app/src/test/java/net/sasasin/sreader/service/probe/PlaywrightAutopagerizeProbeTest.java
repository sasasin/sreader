package net.sasasin.sreader.service.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeEngine;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleCatalog;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleSnapshot;
import net.sasasin.sreader.service.autopagerize.PageSlice;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionSource;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.extraction.HtmlTextExtractor;
import net.sasasin.sreader.service.extraction.PaginatedExtractionResult;
import net.sasasin.sreader.service.extraction.PaginatedHtmlTextExtractor;
import net.sasasin.sreader.service.extraction.TextExtractionOutcome;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.PlaywrightSessionWork;
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentService;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.Test;

class PlaywrightAutopagerizeProbeTest {

  private static final URI ARTICLE_URL = URI.create("https://example.test/article/1");

  @Test
  void articleProbeUsesOnePlaywrightSessionAndPaginatedExtractor() {
    Fixture fixture = fixture();
    AutoPagerizeRuleSnapshot snapshot = snapshot();
    when(fixture.catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    when(fixture.playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(mock(ArticlePageSession.class));
            });
    PaginationResult.Succeeded pagination = successfulPagination();
    when(fixture.engine.paginate(any(), any(), eq(snapshot), any())).thenReturn(pagination);
    when(fixture.paginatedExtractor.extract(
            eq(pagination),
            eq(FullTextMethod.HtmlExtractor.XPATH_OR_BODY_TEXT),
            eq(Optional.empty())))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Extracted(
                    "page one\n\npage two", ExtractionDecision.of(ExtractionSource.PAGE_ELEMENT)),
                List.of()));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            fixture
                .service()
                .probeArticle(
                    ARTICLE_URL, FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE, Optional.empty());

    assertThat(result.text()).isEqualTo("page one\n\npage two");
    assertThat(result.document().finalUrl()).isEqualTo(ARTICLE_URL);
    verify(fixture.engine).paginate(any(), any(), eq(snapshot), any());
    verify(fixture.paginatedExtractor)
        .extract(
            eq(pagination),
            eq(FullTextMethod.HtmlExtractor.XPATH_OR_BODY_TEXT),
            eq(Optional.empty()));
    verify(fixture.playwright).withStandardSession(any());
    verify(fixture.playwright, never()).renderPage(any(), any());
  }

  @Test
  void missingActiveDatasetFailsBeforeOpeningPlaywrightSession() {
    Fixture fixture = fixture();
    when(fixture.catalog.getActiveSnapshot()).thenReturn(Optional.empty());

    ProbeOutcome.Failed result =
        (ProbeOutcome.Failed)
            fixture
                .service()
                .probeArticle(
                    ARTICLE_URL, FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE, Optional.empty());

    assertThat(result.failure().stage()).isEqualTo(FailureStage.LOAD_AUTOPAGERIZE_DATABASE);
    verify(fixture.playwright, never()).withStandardSession(any());
  }

  @Test
  void paginationFailureDoesNotInvokeTextExtraction() {
    Fixture fixture = fixture();
    when(fixture.catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot()));
    when(fixture.playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(mock(ArticlePageSession.class));
            });
    when(fixture.engine.paginate(any(), any(), any(), any()))
        .thenReturn(
            new PaginationResult.Failed(
                Optional.empty(),
                Optional.empty(),
                List.of(),
                PaginationStopReason.FETCH_FAILED,
                OperationFailure.of(
                    FailureStage.FETCH_ARTICLE_PAGE,
                    FailureKind.RENDER,
                    ARTICLE_URL.toString(),
                    "page 2 failed")));

    ProbeOutcome.Failed result =
        (ProbeOutcome.Failed)
            fixture
                .service()
                .probeArticle(
                    ARTICLE_URL, FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE, Optional.empty());

    assertThat(result.failure().message()).contains("page 2 failed");
    verify(fixture.paginatedExtractor, never()).extract(any(), any(), any());
  }

  private static PaginationResult.Succeeded successfulPagination() {
    PageSnapshot first =
        PageSnapshot.ofUtf8(ARTICLE_URL, ARTICLE_URL, "<html><title>Title</title></html>");
    PageSnapshot second =
        PageSnapshot.ofUtf8(
            URI.create("https://example.test/article/2"),
            URI.create("https://example.test/article/2"),
            "<html><body>page two</body></html>");
    return new PaginationResult.Succeeded(
        first,
        Optional.empty(),
        List.of(PageSlice.withoutPageElement(1, first), PageSlice.withoutPageElement(2, second)),
        PaginationStopReason.NO_MATCHING_RULE);
  }

  private static AutoPagerizeRuleSnapshot snapshot() {
    return new AutoPagerizeRuleSnapshot(17L, "a".repeat(64), 1, List.of());
  }

  private static Fixture fixture() {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    PaginatedHtmlTextExtractor paginatedExtractor = mock(PaginatedHtmlTextExtractor.class);
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    FeedDocumentService documents = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor feedExtractor = mock(FeedEntryFullTextExtractor.class);
    FeedReaderProperties properties = properties();
    ProbeDocumentFetcher documentFetcher =
        new ProbeDocumentFetcher(http, playwright, properties, catalog, engine);
    FullTextProbeService service =
        new FullTextProbeService(
            http, documentFetcher, extractor, paginatedExtractor, documents, picker, feedExtractor);
    return new Fixture(service, playwright, paginatedExtractor, catalog, engine);
  }

  private static FeedReaderProperties properties() {
    return new FeedReaderProperties(
        new FeedReaderProperties.Scheduler(false, "0 */15 * * * *"),
        new FeedReaderProperties.Job(false),
        new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            true,
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
        new FeedReaderProperties.Autopagerize(
            20, 5L * 1024 * 1024, 20L * 1024 * 1024, Duration.ofSeconds(30), true),
        List.of());
  }

  private record Fixture(
      FullTextProbeService service,
      PlaywrightHtmlSource playwright,
      PaginatedHtmlTextExtractor paginatedExtractor,
      AutoPagerizeRuleCatalog catalog,
      AutoPagerizeEngine engine) {}
}
