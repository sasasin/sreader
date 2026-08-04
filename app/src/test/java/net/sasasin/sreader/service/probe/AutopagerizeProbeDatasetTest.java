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
import net.sasasin.sreader.service.autopagerize.AutoPagerizeCatalogException;
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
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentService;
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.outcome.FailureStage;
import org.junit.jupiter.api.Test;

class AutopagerizeProbeDatasetTest {

  private static final URI ARTICLE = URI.create("https://example.test/article/1");

  @Test
  void usesExplicitInactiveDatasetIdWithoutTouchingActive() {
    Fixture f = fixture();
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(42L, "d".repeat(64), 1, List.of());
    when(f.catalog.getSnapshot(42L)).thenReturn(snapshot);
    when(f.httpSessionFactory.open()).thenReturn(mock(ArticlePageSession.class));
    PaginationResult.Succeeded pagination = successPagination();
    when(f.engine.paginate(any(), any(), eq(snapshot), any())).thenReturn(pagination);
    when(f.paginated.extract(eq(pagination), any(), eq(Optional.empty())))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Extracted(
                    "body", ExtractionDecision.of(ExtractionSource.BODY_TEXT)),
                List.of()));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            f.service.probeArticle(
                ARTICLE, FullTextMethod.HTTP_AUTOPAGERIZE, Optional.empty(), Optional.of(42L));

    assertThat(result.text()).isEqualTo("body");
    assertThat(result.pagination()).isPresent();
    assertThat(result.pagination().orElseThrow().datasetId()).isEqualTo(42L);
    assertThat(result.pagination().orElseThrow().explicitDatasetSelection()).isTrue();
    verify(f.catalog).getSnapshot(42L);
    verify(f.catalog, never()).getActiveSnapshot();
  }

  @Test
  void missingDatasetIdIsInvalidRequest() {
    Fixture f = fixture();
    when(f.catalog.getSnapshot(999L))
        .thenThrow(new AutoPagerizeCatalogException("AutoPagerize dataset not found: 999"));

    ProbeOutcome.InvalidRequest result =
        (ProbeOutcome.InvalidRequest)
            f.service.probeArticle(
                ARTICLE, FullTextMethod.HTTP_AUTOPAGERIZE, Optional.empty(), Optional.of(999L));

    assertThat(result.message()).contains("not found").contains("999");
  }

  @Test
  void nonAutopagerizeMethodRejectsDatasetOption() {
    Fixture f = fixture();
    ProbeOutcome.InvalidRequest result =
        (ProbeOutcome.InvalidRequest)
            f.service.probeArticle(ARTICLE, FullTextMethod.HTTP, Optional.empty(), Optional.of(1L));
    assertThat(result.message()).contains("--autopagerize-dataset-id");
    verify(f.catalog, never()).getSnapshot(any(Long.class));
  }

  @Test
  void httpAutopagerizeEngineRuntimeBecomesFailed() {
    Fixture f = fixture();
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(7L, "e".repeat(64), 1, List.of());
    when(f.catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    when(f.httpSessionFactory.open()).thenReturn(mock(ArticlePageSession.class));
    when(f.engine.paginate(any(), any(), eq(snapshot), any()))
        .thenThrow(new RuntimeException("engine boom"));

    ProbeOutcome.Failed result =
        (ProbeOutcome.Failed)
            f.service.probeArticle(
                ARTICLE, FullTextMethod.HTTP_AUTOPAGERIZE, Optional.empty(), Optional.empty());
    assertThat(result.failure().stage()).isEqualTo(FailureStage.MATCH_AUTOPAGERIZE_RULE);
    assertThat(result.failure().message()).contains("engine boom");
  }

  @Test
  void explicitDatasetCatalogExceptionWithoutNotFoundIsFailed() {
    Fixture f = fixture();
    when(f.catalog.getSnapshot(55L))
        .thenThrow(new AutoPagerizeCatalogException("compile error in rules"));

    ProbeOutcome outcome =
        f.service.probeArticle(
            ARTICLE, FullTextMethod.HTTP_AUTOPAGERIZE, Optional.empty(), Optional.of(55L));
    assertThat(outcome).isInstanceOf(ProbeOutcome.Failed.class);
    ProbeOutcome.Failed failed = (ProbeOutcome.Failed) outcome;
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.MATCH_AUTOPAGERIZE_RULE);
  }

  @Test
  void xpathOverrideIsPassedToPaginatedExtractor() {
    Fixture f = fixture();
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(7L, "e".repeat(64), 1, List.of());
    when(f.catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    when(f.httpSessionFactory.open()).thenReturn(mock(ArticlePageSession.class));
    PaginationResult.Succeeded pagination = successPagination();
    when(f.engine.paginate(any(), any(), eq(snapshot), any())).thenReturn(pagination);
    when(f.paginated.extract(eq(pagination), any(), eq(Optional.of("//article"))))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Extracted(
                    "xpath-body", ExtractionDecision.of(ExtractionSource.XPATH_OVERRIDE)),
                List.of()));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            f.service.probeArticle(
                ARTICLE,
                FullTextMethod.HTTP_AUTOPAGERIZE,
                Optional.of("//article"),
                Optional.empty());
    assertThat(result.text()).isEqualTo("xpath-body");
    verify(f.paginated)
        .extract(
            eq(pagination),
            eq(FullTextMethod.HtmlExtractor.XPATH_OR_BODY_TEXT),
            eq(Optional.of("//article")));
  }

  @Test
  void paginatedExtractionExceptionBecomesFailed() {
    Fixture f = fixture();
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(7L, "e".repeat(64), 1, List.of());
    when(f.catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    when(f.httpSessionFactory.open()).thenReturn(mock(ArticlePageSession.class));
    PaginationResult.Succeeded pagination = successPagination();
    when(f.engine.paginate(any(), any(), eq(snapshot), any())).thenReturn(pagination);
    when(f.paginated.extract(any(), any(), any())).thenThrow(new RuntimeException("extract boom"));

    ProbeOutcome.Failed result =
        (ProbeOutcome.Failed)
            f.service.probeArticle(
                ARTICLE, FullTextMethod.HTTP_AUTOPAGERIZE, Optional.empty(), Optional.empty());
    assertThat(result.failure().stage()).isEqualTo(FailureStage.EXTRACT_TEXT);
    assertThat(result.failure().message()).contains("AutoPagerize probe extraction failed");
    assertThat(result.failure().cause()).isPresent();
    assertThat(result.pagination()).isPresent();
    assertThat(result.pagination().orElseThrow().pageCount()).isEqualTo(1);
  }

  @Test
  void feedProbeRejectsDatasetOptionForNonAutopagerize() {
    Fixture f = fixture();
    ProbeOutcome.InvalidRequest result =
        (ProbeOutcome.InvalidRequest)
            f.service.probeFeed(
                URI.create("https://example.test/feed.xml"),
                FullTextMethod.FEED,
                net.sasasin.sreader.domain.FeedEntrySelection.first(),
                Optional.empty(),
                Optional.of(1L));
    assertThat(result.message()).contains("--autopagerize-dataset-id");
  }

  @Test
  void activeDatasetPathMarksSelectionAsActive() {
    Fixture f = fixture();
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(7L, "e".repeat(64), 1, List.of());
    when(f.catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    when(f.httpSessionFactory.open()).thenReturn(mock(ArticlePageSession.class));
    PaginationResult.Succeeded pagination = successPagination();
    when(f.engine.paginate(any(), any(), eq(snapshot), any())).thenReturn(pagination);
    when(f.paginated.extract(eq(pagination), any(), eq(Optional.empty())))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Extracted(
                    "body", ExtractionDecision.of(ExtractionSource.BODY_TEXT)),
                List.of()));

    ProbeOutcome.Succeeded result =
        (ProbeOutcome.Succeeded)
            f.service.probeArticle(
                ARTICLE, FullTextMethod.HTTP_AUTOPAGERIZE, Optional.empty(), Optional.empty());

    assertThat(result.pagination().orElseThrow().explicitDatasetSelection()).isFalse();
    assertThat(result.pagination().orElseThrow().datasetId()).isEqualTo(7L);
  }

  private static PaginationResult.Succeeded successPagination() {
    PageSnapshot first = PageSnapshot.ofUtf8(ARTICLE, ARTICLE, "<html><title>T</title></html>");
    return new PaginationResult.Succeeded(
        first,
        Optional.empty(),
        List.of(PageSlice.withoutPageElement(1, first)),
        PaginationStopReason.NO_MATCHING_RULE);
  }

  private static Fixture fixture() {
    HttpFetchService http = mock(HttpFetchService.class);
    HttpArticlePageSessionFactory httpSessionFactory = mock(HttpArticlePageSessionFactory.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    PaginatedHtmlTextExtractor paginated = mock(PaginatedHtmlTextExtractor.class);
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    FeedDocumentService documents = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor feedExtractor = mock(FeedEntryFullTextExtractor.class);
    FeedReaderProperties properties =
        new FeedReaderProperties(
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
    ProbeDocumentFetcher fetcher =
        new ProbeDocumentFetcher(http, httpSessionFactory, playwright, properties, catalog, engine);
    FullTextProbeService service =
        new FullTextProbeService(
            http, fetcher, extractor, paginated, documents, picker, feedExtractor);
    return new Fixture(service, catalog, engine, httpSessionFactory, paginated);
  }

  private record Fixture(
      FullTextProbeService service,
      AutoPagerizeRuleCatalog catalog,
      AutoPagerizeEngine engine,
      HttpArticlePageSessionFactory httpSessionFactory,
      PaginatedHtmlTextExtractor paginated) {}
}
