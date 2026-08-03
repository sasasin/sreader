package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.HttpStatusException;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.Test;

class HttpAutopagerizeExtractionUnitTest {

  @Test
  void catalogExceptionIsRuleMatchFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenThrow(new AutoPagerizeCatalogException("broken"));
    FullTextExtractionService service = service(catalog, mock(HttpArticlePageSessionFactory.class));
    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.MATCH_AUTOPAGERIZE_RULE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.UNEXPECTED);
  }

  @Test
  void unexpectedCatalogRuntimeIsLoadDatabaseFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenThrow(new RuntimeException("db down"));
    FullTextExtractionService service = service(catalog, mock(HttpArticlePageSessionFactory.class));
    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.LOAD_AUTOPAGERIZE_DATABASE);
  }

  @Test
  void invalidFetchUrlIsInvalidInput() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot())
        .thenReturn(Optional.of(new AutoPagerizeRuleSnapshot(1L, "a".repeat(64), 1, List.of())));
    // Illegal character for URI.create
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
    FullTextExtractionService service = service(catalog, mock(HttpArticlePageSessionFactory.class));
    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed) service.extract(bad, FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_ARTICLE_PAGE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);
  }

  @Test
  void sessionRuntimeExceptionIsUnexpectedFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot())
        .thenReturn(Optional.of(new AutoPagerizeRuleSnapshot(1L, "a".repeat(64), 1, List.of())));
    HttpArticlePageSessionFactory factory = mock(HttpArticlePageSessionFactory.class);
    when(factory.open()).thenThrow(new RuntimeException("session boom"));
    FullTextExtractionService service = service(catalog, factory);
    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_ARTICLE_PAGE);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.UNEXPECTED);
  }

  @Test
  void paginationEngineRuntimeIsRuleMatchFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot())
        .thenReturn(Optional.of(new AutoPagerizeRuleSnapshot(1L, "a".repeat(64), 1, List.of())));
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    when(engine.paginate(any(), any(), any(), any())).thenThrow(new IllegalStateException("match"));
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            mock(PaginatedHtmlTextExtractor.class),
            mock(HttpFetchService.class),
            mock(HttpArticlePageSessionFactory.class),
            catalog,
            engine,
            mock(PlaywrightHtmlSource.class),
            properties());

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.MATCH_AUTOPAGERIZE_RULE);
  }

  @Test
  void paginatedExtractionRuntimeIsTextExtractionFailure() {
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(9L, "b".repeat(64), 1, List.of());
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    ArticlePageSession session = mock(ArticlePageSession.class);
    HttpArticlePageSessionFactory factory = mock(HttpArticlePageSessionFactory.class);
    when(factory.open()).thenReturn(session);
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
            factory,
            catalog,
            engine,
            mock(PlaywrightHtmlSource.class),
            properties());

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.EXTRACT_TEXT);
    verify(session).close();
  }

  @Test
  void paginationFailedIsPropagatedWithoutPartialText() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot())
        .thenReturn(Optional.of(new AutoPagerizeRuleSnapshot(9L, "b".repeat(64), 1, List.of())));
    ArticlePageSession session = mock(ArticlePageSession.class);
    HttpArticlePageSessionFactory factory = mock(HttpArticlePageSessionFactory.class);
    when(factory.open()).thenReturn(session);
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
                    FailureKind.HTTP_STATUS,
                    "https://example.test/a",
                    "page 2 failed")));
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            mock(PaginatedHtmlTextExtractor.class),
            mock(HttpFetchService.class),
            factory,
            catalog,
            engine,
            mock(PlaywrightHtmlSource.class),
            properties());
    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(failed.failure().message()).contains("page 2 failed");
    verify(session).close();
  }

  @Test
  void paginationSuccessAttachesMetadata() {
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(9L, "b".repeat(64), 1, List.of());
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    ArticlePageSession session = mock(ArticlePageSession.class);
    HttpArticlePageSessionFactory factory = mock(HttpArticlePageSessionFactory.class);
    when(factory.open()).thenReturn(session);
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
                    "body text", ExtractionDecision.of(ExtractionSource.BODY_TEXT)),
                List.of(
                    new PageTextContribution(
                        1, ExtractionSource.BODY_TEXT, Optional.empty(), "body text"))));
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            paginated,
            mock(HttpFetchService.class),
            factory,
            catalog,
            engine,
            mock(PlaywrightHtmlSource.class),
            properties());
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(extracted.text()).isEqualTo("body text");
    assertThat(extracted.pagination()).isPresent();
    assertThat(extracted.pagination().orElseThrow().datasetId()).isEqualTo(9L);
    assertThat(extracted.pagination().orElseThrow().stopReason())
        .isEqualTo(PaginationStopReason.NO_MATCHING_RULE);
    verify(session).close();
  }

  @Test
  void paginationMetadataRejectsInvalidPageCount() {
    assertThat(
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                    PaginationMetadata.of(
                        1L,
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        PaginationStopReason.NO_MATCHING_RULE,
                        true,
                        List.of())))
        .hasMessageContaining("pageCount");
  }

  @Test
  void paginationNoContentAndFailedAndSkippedAreReturnedAsIs() {
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(9L, "b".repeat(64), 1, List.of());
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    ArticlePageSession session = mock(ArticlePageSession.class);
    HttpArticlePageSessionFactory factory = mock(HttpArticlePageSessionFactory.class);
    when(factory.open()).thenReturn(session);
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
                new TextExtractionOutcome.NoContent(
                    NoContentReason.BODY_TEXT_EMPTY,
                    ExtractionDecision.of(ExtractionSource.BODY_TEXT)),
                List.of()))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Failed(
                    OperationFailure.of(
                        FailureStage.EXTRACT_TEXT,
                        FailureKind.EXTRACTION,
                        "https://example.test/a",
                        "extract failed")),
                List.of()))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Skipped(TextExtractionSkipReason.PLAYWRIGHT_DISABLED),
                List.of()));

    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            paginated,
            mock(HttpFetchService.class),
            factory,
            catalog,
            engine,
            mock(PlaywrightHtmlSource.class),
            properties());

    assertThat(service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE))
        .isInstanceOf(TextExtractionOutcome.NoContent.class);
    assertThat(service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE))
        .isInstanceOf(TextExtractionOutcome.Failed.class);
    assertThat(service.extract(header("https://example.test/a"), FullTextMethod.HTTP_AUTOPAGERIZE))
        .isInstanceOf(TextExtractionOutcome.Skipped.class);
  }

  @Test
  void singlePageHttpStatusFailureMapsHttpStatusKind() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    when(http.get(any())).thenThrow(new HttpStatusException(URI.create("https://x"), 500));
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            mock(PaginatedHtmlTextExtractor.class),
            http,
            mock(HttpArticlePageSessionFactory.class),
            mock(AutoPagerizeRuleCatalog.class),
            mock(AutoPagerizeEngine.class),
            mock(PlaywrightHtmlSource.class),
            properties());
    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(header("https://example.test/a"), FullTextMethod.HTTP);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.HTTP_STATUS);
  }

  private static ContentHeader header(String url) {
    return new ContentHeader("id", "feed", url, url, url, "t", null, null);
  }

  private static FullTextExtractionService service(
      AutoPagerizeRuleCatalog catalog, HttpArticlePageSessionFactory factory) {
    return new FullTextExtractionService(
        mock(ContentHeaderRepository.class),
        mock(ContentFullTextWriter.class),
        mock(HtmlTextExtractor.class),
        mock(PaginatedHtmlTextExtractor.class),
        mock(HttpFetchService.class),
        factory,
        catalog,
        mock(AutoPagerizeEngine.class),
        mock(PlaywrightHtmlSource.class),
        properties());
  }

  private static FeedReaderProperties properties() {
    return new FeedReaderProperties(
        null,
        null,
        new FeedReaderProperties.Http("t", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        null,
        null,
        new FeedReaderProperties.Autopagerize(
            20, 5L * 1024 * 1024, 20L * 1024 * 1024, Duration.ofSeconds(30), true),
        null);
  }
}
