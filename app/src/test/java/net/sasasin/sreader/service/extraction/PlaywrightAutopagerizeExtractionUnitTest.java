package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeCatalogException;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeEngine;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleCatalog;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleSnapshot;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.PlaywrightSessionFailure;
import net.sasasin.sreader.service.extraction.browser.PlaywrightSessionWork;
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaywrightAutopagerizeExtractionUnitTest {

  @Test
  void playwrightDisabledSkipsAutopagerizeMethods() {
    FullTextExtractionService service =
        service(catalogWithSnapshot(), mockEngine(), playwright(false), properties(false));
    TextExtractionOutcome outcome =
        service.extract(header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.Skipped.class);
    assertThat(((TextExtractionOutcome.Skipped) outcome).reason())
        .isEqualTo(TextExtractionSkipReason.PLAYWRIGHT_DISABLED);
  }

  @Test
  void missingActiveDatasetIsConfigurationFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.empty());
    PlaywrightHtmlSource playwright = playwright(true);
    FullTextExtractionService service =
        service(catalog, mockEngine(), playwright, properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.LOAD_AUTOPAGERIZE_DATABASE);
    verify(playwright, never()).withStandardSession(any());
  }

  @Test
  void catalogExceptionIsRuleMatchFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenThrow(new AutoPagerizeCatalogException("broken"));
    FullTextExtractionService service =
        service(catalog, mockEngine(), playwright(true), properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.MATCH_AUTOPAGERIZE_RULE);
  }

  @Test
  void unexpectedCatalogRuntimeIsLoadDatabaseFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenThrow(new RuntimeException("db down"));
    FullTextExtractionService service =
        service(catalog, mockEngine(), playwright(true), properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.LOAD_AUTOPAGERIZE_DATABASE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.UNEXPECTED);
  }

  @Test
  void invalidFetchUrlIsInvalidInput() {
    AutoPagerizeRuleCatalog catalog = catalogWithSnapshot();
    ContentHeader bad =
        new ContentHeader(
            "id",
            "feed",
            "https://example.test/a",
            "http://[invalid",
            "https://example.test/a",
            "t",
            null,
            null);
    FullTextExtractionService service =
        service(catalog, mockEngine(), playwright(true), properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed) service.extract(bad, FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_ARTICLE_PAGE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);
  }

  @Test
  void paginationEngineRuntimeIsRuleMatchFailure() {
    AutoPagerizeRuleCatalog catalog = catalogWithSnapshot();
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    when(playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(mock(ArticlePageSession.class));
            });
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    when(engine.paginate(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("match boom"));
    FullTextExtractionService service = service(catalog, engine, playwright, properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.MATCH_AUTOPAGERIZE_RULE);
  }

  @Test
  void paginatedExtractionRuntimeIsTextExtractionFailure() {
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(9L, "b".repeat(64), 1, List.of());
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    when(playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(mock(ArticlePageSession.class));
            });
    PageSnapshot first =
        PageSnapshot.ofUtf8(
            URI.create("https://example.test/a"), URI.create("https://example.test/a"), "<html/>");
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    when(engine.paginate(any(), any(), any(), any()))
        .thenReturn(
            new PaginationResult.Succeeded(
                first,
                Optional.empty(),
                List.of(
                    net.sasasin.sreader.service.autopagerize.PageSlice.withoutPageElement(
                        1, first)),
                PaginationStopReason.NO_MATCHING_RULE));
    PaginatedHtmlTextExtractor paginated = mock(PaginatedHtmlTextExtractor.class);
    when(paginated.extract(any(), any(), any())).thenThrow(new IllegalStateException("extract"));

    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            paginated,
            mock(HttpFetchService.class),
            mock(HttpArticlePageSessionFactory.class),
            catalog,
            engine,
            playwright,
            properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.EXTRACT_TEXT);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.EXTRACTION);
  }

  @Test
  void sessionWorkRunsEngineAndClosesViaFacade() {
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(7L, "c".repeat(64), 1, List.of());
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    ArticlePageSession session = mock(ArticlePageSession.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    when(playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(session);
            });
    PageSnapshot first =
        PageSnapshot.ofUtf8(
            URI.create("https://example.test/a"),
            URI.create("https://example.test/a"),
            "<html><body>p1</body></html>");
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    when(engine.paginate(any(), any(), any(), any()))
        .thenReturn(
            new PaginationResult.Succeeded(
                first,
                Optional.empty(),
                List.of(
                    net.sasasin.sreader.service.autopagerize.PageSlice.withoutPageElement(
                        1, first)),
                PaginationStopReason.NO_MATCHING_RULE));
    PaginatedHtmlTextExtractor paginated = mock(PaginatedHtmlTextExtractor.class);
    when(paginated.extract(any(), any(), any()))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Extracted(
                    "joined", ExtractionDecision.of(ExtractionSource.BODY_TEXT)),
                List.of(
                    new PageTextContribution(
                        1, ExtractionSource.BODY_TEXT, Optional.empty(), "joined"))));

    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            paginated,
            mock(HttpFetchService.class),
            mock(HttpArticlePageSessionFactory.class),
            catalog,
            engine,
            playwright,
            properties(true));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(extracted.text()).isEqualTo("joined");
    assertThat(extracted.pagination()).isPresent();
    assertThat(extracted.pagination().orElseThrow().datasetId()).isEqualTo(7L);
    assertThat(extracted.pagination().orElseThrow().stopReason())
        .isEqualTo(PaginationStopReason.NO_MATCHING_RULE);
    ArgumentCaptor<ArticlePageSession> sessionCaptor =
        ArgumentCaptor.forClass(ArticlePageSession.class);
    verify(engine).paginate(any(), sessionCaptor.capture(), any(), any());
    assertThat(sessionCaptor.getValue()).isSameAs(session);
    verify(playwright).withStandardSession(any());
  }

  @Test
  void paginationFailedIsPropagatedWithoutPartialText() {
    AutoPagerizeRuleCatalog catalog = catalogWithSnapshot();
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    when(playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(mock(ArticlePageSession.class));
            });
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    when(engine.paginate(any(), any(), any(), any()))
        .thenReturn(
            new PaginationResult.Failed(
                Optional.empty(),
                Optional.empty(),
                List.of(),
                PaginationStopReason.FETCH_FAILED,
                OperationFailure.of(
                    FailureStage.FETCH_ARTICLE_PAGE,
                    FailureKind.RENDER,
                    "https://example.test/a",
                    "page 2 navigation failed")));
    FullTextExtractionService service = service(catalog, engine, playwright, properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().message()).contains("page 2 navigation failed");
  }

  @Test
  void structuredPaginationFailureSurvivesSessionCloseFailure() {
    AutoPagerizeRuleCatalog catalog = catalogWithSnapshot();
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    OperationFailure paginationFailure =
        OperationFailure.of(
            FailureStage.FETCH_ARTICLE_PAGE,
            FailureKind.RENDER,
            "https://example.test/a",
            "page 2 navigation failed");
    PlaywrightSessionFailure primary = new PlaywrightSessionFailure(paginationFailure);
    primary.addSuppressed(new RuntimeException("context close failed"));
    when(playwright.withStandardSession(any())).thenThrow(primary);
    FullTextExtractionService service =
        service(catalog, mockEngine(), playwright, properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_ARTICLE_PAGE);
    assertThat(failed.failure().message()).isEqualTo("page 2 navigation failed");
  }

  @Test
  void sessionOpenFailureIsRenderFailure() {
    AutoPagerizeRuleCatalog catalog = catalogWithSnapshot();
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    when(playwright.withStandardSession(any())).thenThrow(new RuntimeException("context boom"));
    FullTextExtractionService service =
        service(catalog, mockEngine(), playwright, properties(true));

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header("https://example.test/a"), FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.RENDER_ARTICLE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.RENDER);
  }

  @Test
  void existingStandardPlaywrightStillUsesRender() {
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    when(playwright.render(any())).thenReturn("<html><body>solo</body></html>");
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    when(extractor.extract(any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "solo", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            extractor,
            mock(PaginatedHtmlTextExtractor.class),
            mock(HttpFetchService.class),
            mock(HttpArticlePageSessionFactory.class),
            mock(AutoPagerizeRuleCatalog.class),
            mock(AutoPagerizeEngine.class),
            playwright,
            properties(true));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(header("https://example.test/a"), FullTextMethod.PLAYWRIGHT);

    assertThat(extracted.text()).isEqualTo("solo");
    verify(playwright).render(URI.create("https://example.test/a"));
    verify(playwright, never()).withStandardSession(any());
  }

  @Test
  void readabilityMethodUsesPaginatedExtractor() {
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(3L, "d".repeat(64), 1, List.of());
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    when(playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(mock(ArticlePageSession.class));
            });
    PageSnapshot first =
        PageSnapshot.ofUtf8(
            URI.create("https://example.test/a"), URI.create("https://example.test/a"), "<html/>");
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    when(engine.paginate(any(), any(), any(), any()))
        .thenReturn(
            new PaginationResult.Succeeded(
                first,
                Optional.empty(),
                List.of(
                    net.sasasin.sreader.service.autopagerize.PageSlice.withoutPageElement(
                        1, first)),
                PaginationStopReason.NO_MATCHING_RULE));
    PaginatedHtmlTextExtractor paginated = mock(PaginatedHtmlTextExtractor.class);
    when(paginated.extract(any(), any(), any()))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Extracted(
                    "readability-or-pe", ExtractionDecision.of(ExtractionSource.READABILITY)),
                List.of()));

    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            paginated,
            mock(HttpFetchService.class),
            mock(HttpArticlePageSessionFactory.class),
            catalog,
            engine,
            playwright,
            properties(true));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(
                header("https://example.test/a"),
                FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE_READABILITY);

    assertThat(extracted.text()).isEqualTo("readability-or-pe");
    verify(paginated)
        .extract(
            any(),
            org.mockito.ArgumentMatchers.eq(FullTextMethod.HtmlExtractor.READABILITY),
            any());
  }

  private static AutoPagerizeRuleCatalog catalogWithSnapshot() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot())
        .thenReturn(Optional.of(new AutoPagerizeRuleSnapshot(1L, "a".repeat(64), 1, List.of())));
    return catalog;
  }

  private static AutoPagerizeEngine mockEngine() {
    return mock(AutoPagerizeEngine.class);
  }

  private static PlaywrightHtmlSource playwright(boolean enabled) {
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    if (!enabled) {
      return playwright;
    }
    when(playwright.withStandardSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              return work.apply(mock(ArticlePageSession.class));
            });
    return playwright;
  }

  private static FullTextExtractionService service(
      AutoPagerizeRuleCatalog catalog,
      AutoPagerizeEngine engine,
      PlaywrightHtmlSource playwright,
      FeedReaderProperties properties) {
    return new FullTextExtractionService(
        mock(ContentHeaderRepository.class),
        mock(ContentFullTextWriter.class),
        mock(HtmlTextExtractor.class),
        mock(PaginatedHtmlTextExtractor.class),
        mock(HttpFetchService.class),
        mock(HttpArticlePageSessionFactory.class),
        catalog,
        engine,
        playwright,
        properties);
  }

  private static ContentHeader header(String url) {
    return new ContentHeader("id", "feed", url, url, url, "t", null, null);
  }

  private static FeedReaderProperties properties(boolean playwrightEnabled) {
    return new FeedReaderProperties(
        null,
        null,
        new FeedReaderProperties.Http("t", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            playwrightEnabled, true, 800, 600, Duration.ofSeconds(3), Duration.ofSeconds(2)),
        null,
        new FeedReaderProperties.Autopagerize(
            20, 5L * 1024 * 1024, 20L * 1024 * 1024, Duration.ofSeconds(30), true),
        null);
  }
}
