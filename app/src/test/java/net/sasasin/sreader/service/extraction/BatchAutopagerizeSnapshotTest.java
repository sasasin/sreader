package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeEngine;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleCatalog;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleSnapshot;
import net.sasasin.sreader.service.autopagerize.PageSlice;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchAutopagerizeSnapshotTest {

  @Test
  void batchFreezesActiveSnapshotAndDoesNotReloadPerItem() {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    AutoPagerizeEngine engine = mock(AutoPagerizeEngine.class);
    HttpArticlePageSessionFactory factory = mock(HttpArticlePageSessionFactory.class);
    ArticlePageSession session = mock(ArticlePageSession.class);
    PaginatedHtmlTextExtractor paginated = mock(PaginatedHtmlTextExtractor.class);

    ContentHeader first = header("id1", "https://example.test/1");
    ContentHeader second = header("id2", "https://example.test/2");
    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(first, FullTextMethod.HTTP_AUTOPAGERIZE),
                new PendingFullTextTarget(second, FullTextMethod.HTTP_AUTOPAGERIZE)));

    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(99L, "a".repeat(64), 1, List.of());
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    when(factory.open()).thenReturn(session);

    PageSnapshot page =
        PageSnapshot.ofUtf8(
            URI.create("https://example.test/1"), URI.create("https://example.test/1"), "<html/>");
    PaginationResult.Succeeded pagination =
        new PaginationResult.Succeeded(
            page,
            Optional.empty(),
            List.of(PageSlice.withoutPageElement(1, page)),
            PaginationStopReason.NO_MATCHING_RULE);
    when(engine.paginate(any(), any(), eq(snapshot), any())).thenReturn(pagination);
    when(paginated.extract(eq(pagination), any(), eq(Optional.empty())))
        .thenReturn(
            new PaginatedExtractionResult(
                new TextExtractionOutcome.Extracted(
                    "text", ExtractionDecision.of(ExtractionSource.BODY_TEXT)),
                List.of(
                    new PageTextContribution(
                        1, ExtractionSource.BODY_TEXT, Optional.empty(), "text"))));
    when(writer.saveIfAbsent(any(), any(), any())).thenReturn(ContentFullTextWriteOutcome.INSERTED);

    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            mock(HtmlTextExtractor.class),
            paginated,
            mock(HttpFetchService.class),
            factory,
            catalog,
            engine,
            mock(PlaywrightHtmlSource.class),
            properties());

    FullTextExtractionBatchResult result = service.extractPending(10);
    assertThat(result.inserted()).isEqualTo(2);
    verify(catalog, times(1)).getActiveSnapshot();
    verify(catalog, never()).getSnapshot(any(Long.class));
    ArgumentCaptor<AutoPagerizeRuleSnapshot> snapCaptor =
        ArgumentCaptor.forClass(AutoPagerizeRuleSnapshot.class);
    verify(engine, times(2)).paginate(any(), any(), snapCaptor.capture(), any());
    assertThat(snapCaptor.getAllValues()).containsOnly(snapshot);
  }

  @Test
  void batchWithoutAutopagerizeTargetsDoesNotLoadCatalog() throws Exception {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);

    ContentHeader header = header("id1", "https://example.test/1");
    when(repository.findWithoutFullTextForUrlExtraction(5))
        .thenReturn(List.of(new PendingFullTextTarget(header, FullTextMethod.HTTP)));
    when(http.get(any()))
        .thenReturn(
            new HttpFetchService.FetchedResource(
                URI.create("https://example.test/1"), "<html><body>x</body></html>"));
    when(extractor.extract(any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "x", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));
    when(writer.saveIfAbsent(any(), any(), any())).thenReturn(ContentFullTextWriteOutcome.INSERTED);

    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            extractor,
            mock(PaginatedHtmlTextExtractor.class),
            http,
            mock(HttpArticlePageSessionFactory.class),
            catalog,
            mock(AutoPagerizeEngine.class),
            mock(PlaywrightHtmlSource.class),
            properties());

    assertThat(service.extractPending(5).inserted()).isEqualTo(1);
    verify(catalog, never()).getActiveSnapshot();
  }

  @Test
  void missingActiveDatasetFailsOnlyAutopagerizeItems() throws Exception {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);

    ContentHeader httpHeader = header("id1", "https://example.test/http");
    ContentHeader apHeader = header("id2", "https://example.test/ap");
    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(httpHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(apHeader, FullTextMethod.HTTP_AUTOPAGERIZE)));
    when(catalog.getActiveSnapshot()).thenReturn(Optional.empty());
    when(http.get(any()))
        .thenReturn(
            new HttpFetchService.FetchedResource(
                URI.create("https://example.test/http"), "<html/>"));
    when(extractor.extract(any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "ok", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));
    when(writer.saveIfAbsent(any(), any(), any())).thenReturn(ContentFullTextWriteOutcome.INSERTED);

    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            extractor,
            mock(PaginatedHtmlTextExtractor.class),
            http,
            mock(HttpArticlePageSessionFactory.class),
            catalog,
            mock(AutoPagerizeEngine.class),
            mock(PlaywrightHtmlSource.class),
            properties());

    FullTextExtractionBatchResult result = service.extractPending(10);
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    verify(writer, times(1)).saveIfAbsent(eq(httpHeader), eq(FullTextMethod.HTTP), any());
    verify(writer, never()).saveIfAbsent(eq(apHeader), any(), any());
  }

  private static ContentHeader header(String id, String url) {
    return new ContentHeader(id, "feed", url, url, url, "t", null, null);
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
}
