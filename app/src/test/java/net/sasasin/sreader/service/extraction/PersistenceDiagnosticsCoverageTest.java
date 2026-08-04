package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeCatalogException;
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
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PersistenceDiagnosticsCoverageTest {

  @Test
  void contentFullTextFactoriesAndValidation() {
    ContentFullText plain =
        ContentFullText.success("id1", "h1", "text", "http", "body_text", "https://ex/a");
    assertThat(plain.paginationComplete()).isNull();
    assertThat(plain.errorMessage()).isNull();

    ContentFullText ap =
        ContentFullText.successAutopagerize(
            "id2",
            "h1",
            "text",
            "http_autopagerize",
            "page_element",
            "https://ex/a",
            3L,
            Optional.of(1),
            2,
            "NO_NEXT_LINK",
            true);
    assertThat(ap.autopagerizeRuleOrdinal()).isEqualTo(1);
    assertThat(ap.paginationPageCount()).isEqualTo(2);

    assertThatThrownBy(() -> ContentFullText.success("id", "h", "t", " ", "body_text", "https://x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ContentFullText.success("id", "h", "t", "http", " ", "https://x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ContentFullText.success("id", "h", "t", "http", "body_text", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ContentFullText(
                    "id",
                    "h",
                    "t",
                    "http",
                    "success",
                    null,
                    "body_text",
                    "https://x",
                    null,
                    1,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("autopagerizeRuleOrdinal");
    assertThatThrownBy(
            () ->
                ContentFullText.successAutopagerize(
                    "id",
                    "h",
                    "t",
                    "http_autopagerize",
                    "body_text",
                    "https://x",
                    1L,
                    Optional.empty(),
                    0,
                    "NO_MATCHING_RULE",
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pageCount");
    assertThatThrownBy(
            () ->
                ContentFullText.successAutopagerize(
                    "id",
                    "h",
                    "t",
                    "http_autopagerize",
                    "body_text",
                    "https://x",
                    1L,
                    Optional.empty(),
                    1,
                    " ",
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stopReason");
  }

  @Test
  void paginationMetadataAndPageTraceValidation() {
    assertThatThrownBy(
            () -> new PaginationPageTrace(0, URI.create("https://a"), URI.create("https://a"), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new PaginationPageTrace(1, URI.create("https://a"), URI.create("https://a"), -1))
        .isInstanceOf(IllegalArgumentException.class);

    PaginationPageTrace page =
        new PaginationPageTrace(1, URI.create("https://a"), URI.create("https://b"), 5);
    PaginationMetadata meta =
        new PaginationMetadata(
            1L,
            "a".repeat(64),
            1,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1,
            PaginationStopReason.NO_MATCHING_RULE,
            true,
            List.of(page),
            Optional.empty(),
            List.of());
    assertThat(meta.lastPageFinalUrl()).contains(URI.create("https://b"));
    assertThat(meta.totalBytes()).isEqualTo(5);

    PaginationMetadata emptyPages =
        new PaginationMetadata(
            1L,
            "a".repeat(64),
            1,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            PaginationStopReason.FETCH_FAILED,
            false,
            List.of(),
            Optional.of(URI.create("https://fail")),
            List.of());
    assertThat(emptyPages.lastPageFinalUrl()).isEmpty();
    assertThat(emptyPages.totalBytes()).isZero();

    assertThatThrownBy(
            () ->
                new PaginationMetadata(
                    1L,
                    "not-hex",
                    1,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    1,
                    PaginationStopReason.NO_MATCHING_RULE,
                    true,
                    List.of(),
                    Optional.empty(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PaginationMetadata(
                    1L,
                    "a".repeat(64),
                    0,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    1,
                    PaginationStopReason.NO_MATCHING_RULE,
                    true,
                    List.of(),
                    Optional.empty(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void paginationMetadataFactoryFromFailedUsesExplicitRequestedUriAndFirstPageTrace() {
    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(3L, "b".repeat(64), 1, List.of());
    PageSnapshot first =
        PageSnapshot.ofUtf8(
            URI.create("https://example.test/1"), URI.create("https://example.test/1"), "<html/>");
    PaginationResult.Failed failed =
        new PaginationResult.Failed(
            Optional.of(first),
            Optional.empty(),
            List.of(PageSlice.withoutPageElement(1, first)),
            PaginationStopReason.FETCH_FAILED,
            OperationFailure.of(
                FailureStage.FETCH_ARTICLE_PAGE, FailureKind.IO, "https://example.test/1", "boom"),
            Optional.of(URI.create("https://example.test/2")));
    PaginationMetadata meta = PaginationMetadataFactory.fromFailed(failed, snapshot, true);
    assertThat(meta.complete()).isFalse();
    assertThat(meta.explicitDatasetSelection()).isTrue();
    assertThat(meta.pageCount()).isEqualTo(1);
    assertThat(meta.failedRequestedUrl()).contains(URI.create("https://example.test/2"));
    assertThat(meta.pages()).hasSize(1);

    PaginationResult.Failed badSubject =
        new PaginationResult.Failed(
            Optional.empty(),
            Optional.empty(),
            List.of(),
            PaginationStopReason.FETCH_FAILED,
            OperationFailure.of(
                FailureStage.FETCH_ARTICLE_PAGE, FailureKind.IO, "not a uri ://", "boom"));
    PaginationMetadata meta2 = PaginationMetadataFactory.fromFailed(badSubject, snapshot, false);
    assertThat(meta2.failedRequestedUrl()).isEmpty();

    PaginationResult.Failed firstPageOnly =
        new PaginationResult.Failed(
            Optional.of(first),
            Optional.empty(),
            List.of(),
            PaginationStopReason.TIMEOUT,
            OperationFailure.of(
                FailureStage.FETCH_ARTICLE_PAGE,
                FailureKind.IO,
                "https://example.test/1",
                "timeout"));
    PaginationMetadata firstPageMeta =
        PaginationMetadataFactory.fromFailed(firstPageOnly, snapshot, false);
    assertThat(firstPageMeta.pageCount()).isEqualTo(1);
    assertThat(firstPageMeta.lastPageFinalUrl()).contains(URI.create("https://example.test/1"));
  }

  @Test
  void writerUsesFetchUrlWhenPaginationHasNoPages() {
    ContentFullTextRepository repository = mock(ContentFullTextRepository.class);
    ContentFullTextWriter writer = new ContentFullTextWriter(repository);
    ContentHeader header =
        new ContentHeader("h", "f", "https://src", "https://fetch", "https://can", "t", null, null);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    PaginationMetadata meta =
        new PaginationMetadata(
            9L,
            "a".repeat(64),
            1,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            PaginationStopReason.NO_MATCHING_RULE,
            true,
            List.of(),
            Optional.empty(),
            List.of());
    TextExtractionOutcome.Extracted extracted =
        new TextExtractionOutcome.Extracted(
            "body",
            ExtractionDecision.of(ExtractionSource.BODY_TEXT),
            Optional.of(meta),
            Optional.empty());
    writer.saveIfAbsent(header, FullTextMethod.HTTP_AUTOPAGERIZE, extracted);
    ArgumentCaptor<ContentFullText> captor = ArgumentCaptor.forClass(ContentFullText.class);
    verify(repository).insertIfAbsent(captor.capture());
    assertThat(captor.getValue().extractedUrl()).isEqualTo("https://fetch");
    assertThat(captor.getValue().paginationPageCount()).isEqualTo(1);
  }

  @Test
  void writerClampsZeroPageCountAndUsesPaginationFirstFinalUrl() {
    ContentFullTextRepository repository = mock(ContentFullTextRepository.class);
    ContentFullTextWriter writer = new ContentFullTextWriter(repository);
    ContentHeader header =
        new ContentHeader("h", "f", "https://src", "https://fetch", "https://can", "t", null, null);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    PaginationMetadata meta =
        new PaginationMetadata(
            9L,
            "a".repeat(64),
            1,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            PaginationStopReason.NO_MATCHING_RULE,
            true,
            List.of(
                new PaginationPageTrace(
                    1, URI.create("https://req"), URI.create("https://final-from-page"), 1)),
            Optional.empty(),
            List.of());
    TextExtractionOutcome.Extracted extracted =
        new TextExtractionOutcome.Extracted(
            "body",
            ExtractionDecision.of(ExtractionSource.BODY_TEXT),
            Optional.of(meta),
            Optional.empty());
    assertThat(writer.saveIfAbsent(header, FullTextMethod.HTTP_AUTOPAGERIZE, extracted))
        .isEqualTo(ContentFullTextWriteOutcome.INSERTED);
    ArgumentCaptor<ContentFullText> captor = ArgumentCaptor.forClass(ContentFullText.class);
    verify(repository).insertIfAbsent(captor.capture());
    assertThat(captor.getValue().paginationPageCount()).isEqualTo(1);
    assertThat(captor.getValue().extractedUrl()).isEqualTo("https://fetch");
  }

  @Test
  void writerFallsBackToFetchUrlAndInsertsAlreadyExists() {
    ContentFullTextRepository repository = mock(ContentFullTextRepository.class);
    ContentFullTextWriter writer = new ContentFullTextWriter(repository);
    ContentHeader header =
        new ContentHeader("h", "f", "https://src", "https://fetch", "https://can", "t", null, null);
    when(repository.insertIfAbsent(any())).thenReturn(false);

    TextExtractionOutcome.Extracted extracted =
        new TextExtractionOutcome.Extracted(
            "body", ExtractionDecision.of(ExtractionSource.BODY_TEXT));
    assertThat(writer.saveIfAbsent(header, FullTextMethod.HTTP, extracted))
        .isEqualTo(ContentFullTextWriteOutcome.ALREADY_EXISTS);

    ArgumentCaptor<ContentFullText> captor = ArgumentCaptor.forClass(ContentFullText.class);
    verify(repository).insertIfAbsent(captor.capture());
    assertThat(captor.getValue().extractedUrl()).isEqualTo("https://fetch");
  }

  @Test
  void batchCatalogRuntimeFailureFailsOnlyAutopagerizeTargets() throws Exception {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);

    ContentHeader httpHeader =
        new ContentHeader("id1", "feed", "https://h", "https://h", "https://h", "t", null, null);
    ContentHeader apHeader =
        new ContentHeader("id2", "feed", "https://ap", "https://ap", "https://ap", "t", null, null);
    when(repository.findWithoutFullTextForUrlExtraction(5))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(httpHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(apHeader, FullTextMethod.HTTP_AUTOPAGERIZE)));
    when(catalog.getActiveSnapshot()).thenThrow(new RuntimeException("db down"));
    when(http.get(any()))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://h"), "<html/>"));
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

    FullTextExtractionBatchResult result = service.extractPending(5);
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
  }

  @Test
  void batchCatalogLoadFailureFailsOnlyAutopagerizeTargets() throws Exception {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);

    ContentHeader httpHeader =
        new ContentHeader("id1", "feed", "https://h", "https://h", "https://h", "t", null, null);
    ContentHeader apHeader =
        new ContentHeader("id2", "feed", "https://ap", "https://ap", "https://ap", "t", null, null);
    when(repository.findWithoutFullTextForUrlExtraction(5))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(httpHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(apHeader, FullTextMethod.HTTP_AUTOPAGERIZE)));
    when(catalog.getActiveSnapshot()).thenThrow(new AutoPagerizeCatalogException("broken"));
    when(http.get(any()))
        .thenReturn(new HttpFetchService.FetchedResource(URI.create("https://h"), "<html/>"));
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

    FullTextExtractionBatchResult result = service.extractPending(5);
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    verify(writer).saveIfAbsent(any(ContentHeader.class), any(FullTextMethod.class), any());
  }

  @Test
  void extractedWithHelpersPreserveFields() {
    TextExtractionOutcome.Extracted base =
        new TextExtractionOutcome.Extracted("t", ExtractionDecision.of(ExtractionSource.BODY_TEXT));
    PaginationMetadata meta =
        new PaginationMetadata(
            1L,
            "c".repeat(64),
            1,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1,
            PaginationStopReason.NO_MATCHING_RULE,
            true,
            List.of(
                new PaginationPageTrace(1, URI.create("https://a"), URI.create("https://a"), 1)),
            Optional.empty(),
            List.of());
    TextExtractionOutcome.Extracted withMeta =
        base.withPagination(meta).withExtractedUrl("https://a");
    assertThat(withMeta.pagination()).contains(meta);
    assertThat(withMeta.extractedUrl()).contains("https://a");

    TextExtractionOutcome.Failed failed =
        new TextExtractionOutcome.Failed(
                OperationFailure.of(FailureStage.FETCH_ARTICLE_PAGE, FailureKind.IO, "s", "m"))
            .withPagination(meta);
    assertThat(failed.pagination()).contains(meta);
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
