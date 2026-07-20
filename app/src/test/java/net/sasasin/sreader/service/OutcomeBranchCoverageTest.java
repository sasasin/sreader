package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.dankito.readability4j.Article;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** Extra branch coverage for P0-3 outcome paths. */
class OutcomeBranchCoverageTest {

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
  void feedReaderLogsFailedFeedWithoutCauseAndContinues() {
    FeedRegistrationService registration = mock(FeedRegistrationService.class);
    FeedUrlRepository feeds = mock(FeedUrlRepository.class);
    FeedEntryImportService importService = mock(FeedEntryImportService.class);
    FullTextExtractionService extraction = mock(FullTextExtractionService.class);
    ContentTextFileExportService export = mock(ContentTextFileExportService.class);
    FeedUrl failed = new FeedUrl("f1", "https://example.test/bad.xml");
    when(registration.registerFeedUrls(any())).thenReturn(0);
    when(feeds.findActiveForReading()).thenReturn(List.of(failed));
    when(importService.importEntries(failed))
        .thenReturn(
            new FeedImportResult.Failed(
                FeedImportSummary.empty(),
                OperationFailure.of(
                    FailureStage.PARSE_FEED, FailureKind.PARSE, failed.url(), "bad xml")));
    when(extraction.extractPending(100)).thenReturn(FullTextExtractionBatchResult.empty());
    when(export.exportPending(10)).thenReturn(0);

    FeedReaderService.JobResult result =
        new FeedReaderService(
                propertiesExport(10), registration, feeds, importService, extraction, export)
            .runOnce();
    assertThat(result.feedImport().failedFeeds()).isEqualTo(1);
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
  void feedImportSummaryCountsMissingLinkExistingRedirectAndFeedTextOutcomes() {
    URI source = URI.create("https://example.test/a");
    RedirectResolution.Fallback fallback =
        new RedirectResolution.Fallback(
            source,
            OperationFailure.of(
                FailureStage.RESOLVE_REDIRECT, FailureKind.IO, source.toString(), "down"));
    RedirectResolution.Resolved resolved =
        new RedirectResolution.Resolved(source, URI.create("https://final"));

    FeedImportSummary summary = FeedImportSummary.empty();
    summary = summary.plusEntry(new FeedEntryImportOutcome.MissingLink());
    summary =
        summary.plusEntry(
            new FeedEntryImportOutcome.Inserted(
                fallback, Optional.of(ContentFullTextWriteOutcome.INSERTED)));
    summary =
        summary.plusEntry(
            new FeedEntryImportOutcome.Inserted(
                resolved, Optional.of(ContentFullTextWriteOutcome.ALREADY_EXISTS)));
    summary =
        summary.plusEntry(
            new FeedEntryImportOutcome.Inserted(
                resolved, Optional.of(ContentFullTextWriteOutcome.NO_CONTENT)));
    summary = summary.plusEntry(new FeedEntryImportOutcome.Inserted(resolved, Optional.empty()));
    summary = summary.plusEntry(new FeedEntryImportOutcome.AlreadyPresent(fallback));
    summary =
        summary.plusEntry(
            new FeedEntryImportOutcome.Failed(
                OperationFailure.of(
                    FailureStage.PERSIST_HEADER, FailureKind.PERSISTENCE, "x", "db")));

    assertThat(summary.entriesSeen()).isEqualTo(7);
    assertThat(summary.missingLinks()).isEqualTo(1);
    assertThat(summary.insertedHeaders()).isEqualTo(4);
    assertThat(summary.existingHeaders()).isEqualTo(1);
    assertThat(summary.redirectFallbacks()).isEqualTo(2);
    assertThat(summary.feedTextsInserted()).isEqualTo(1);
    assertThat(summary.feedTextsAlreadyPresent()).isEqualTo(1);
    assertThat(summary.feedTextsWithoutContent()).isEqualTo(1);
  }

  @Test
  void feedEntryImporterCoversInvalidLinkPersistenceAndFeedTextPaths() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository headers = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FeedEntryImporter importer =
        new FeedEntryImporter(http, new ArticleUrlCanonicalizer(), headers, extractor, writer);

    SyndEntryImpl missing = new SyndEntryImpl();
    assertThat(importer.importEntry(feed("http"), missing))
        .isInstanceOf(FeedEntryImportOutcome.MissingLink.class);

    SyndEntryImpl badLink = new SyndEntryImpl();
    badLink.setLink("http://[");
    assertThat(importer.importEntry(feed("http"), badLink))
        .isInstanceOf(FeedEntryImportOutcome.Failed.class);

    URI article = URI.create("https://example.test/a");
    SyndEntryImpl entry = new SyndEntryImpl();
    entry.setLink(article.toString());
    entry.setTitle("T");
    when(http.resolveRedirect(article))
        .thenReturn(new RedirectResolution.Resolved(article, article));
    when(extractor.extract(entry))
        .thenReturn(
            new TextExtractionOutcome.NoContent(
                NoContentReason.FEED_CONTENT_MISSING,
                ExtractionDecision.of(ExtractionSource.FEED)));
    doReturn(ContentHeaderUpsertOutcome.INSERTED).when(headers).insertOrRefreshFetchUrl(any());
    doReturn(ContentFullTextWriteOutcome.NO_CONTENT).when(writer).saveIfAbsent(any(), eq(null));

    FeedEntryImportOutcome.Inserted inserted =
        (FeedEntryImportOutcome.Inserted) importer.importEntry(feed("feed"), entry);
    assertThat(inserted.feedTextWrite()).contains(ContentFullTextWriteOutcome.NO_CONTENT);

    doThrow(new DataAccessResourceFailureException("header fail"))
        .when(headers)
        .insertOrRefreshFetchUrl(any());
    FeedEntryImportOutcome.Failed headerFail =
        (FeedEntryImportOutcome.Failed) importer.importEntry(feed("feed"), entry);
    assertThat(headerFail.failure().stage()).isEqualTo(FailureStage.PERSIST_HEADER);

    doReturn(ContentHeaderUpsertOutcome.INSERTED).when(headers).insertOrRefreshFetchUrl(any());
    when(extractor.extract(entry))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "body", ExtractionDecision.of(ExtractionSource.FEED)));
    doThrow(new DataAccessResourceFailureException("ft fail"))
        .when(writer)
        .saveIfAbsent(any(), eq("body"));
    FeedEntryImportOutcome.Failed textFail =
        (FeedEntryImportOutcome.Failed) importer.importEntry(feed("feed"), entry);
    assertThat(textFail.failure().stage()).isEqualTo(FailureStage.PERSIST_FULL_TEXT);

    when(http.resolveRedirect(article))
        .thenReturn(
            new RedirectResolution.Fallback(
                article,
                OperationFailure.of(
                    FailureStage.RESOLVE_REDIRECT,
                    FailureKind.INTERRUPTED,
                    article.toString(),
                    "stop")));
    FeedEntryImportOutcome.Failed interrupted =
        (FeedEntryImportOutcome.Failed) importer.importEntry(feed("http"), entry);
    assertThat(interrupted.failure().kind()).isEqualTo(FailureKind.INTERRUPTED);

    when(http.resolveRedirect(article))
        .thenReturn(new RedirectResolution.Resolved(article, article));
    doReturn(ContentHeaderUpsertOutcome.EXISTING_REFRESHED)
        .when(headers)
        .insertOrRefreshFetchUrl(any());
    entry.setPublishedDate(new java.util.Date());
    assertThat(importer.importEntry(feed("http"), entry))
        .isInstanceOf(FeedEntryImportOutcome.AlreadyPresent.class);
  }

  @Test
  void feedEntryImporterPropagatesFeedTextExtractionFailure() {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository headers = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    FeedEntryImporter importer =
        new FeedEntryImporter(
            http,
            new ArticleUrlCanonicalizer(),
            headers,
            extractor,
            mock(ContentFullTextWriter.class));
    URI article = URI.create("https://example.test/a");
    SyndEntryImpl entry = new SyndEntryImpl();
    entry.setLink(article.toString());
    when(http.resolveRedirect(article))
        .thenReturn(new RedirectResolution.Resolved(article, article));
    when(extractor.extract(entry))
        .thenReturn(
            new TextExtractionOutcome.Failed(
                OperationFailure.of(
                    FailureStage.EXTRACT_TEXT,
                    FailureKind.EXTRACTION,
                    article.toString(),
                    "bad feed text")));

    FeedEntryImportOutcome.Failed failed =
        (FeedEntryImportOutcome.Failed) importer.importEntry(feed("feed"), entry);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.EXTRACT_TEXT);
    verify(headers, never()).insertOrRefreshFetchUrl(any());
  }

  @Test
  void feedEntryImporterRejectsUnexpectedFeedTextSkip() {
    HttpFetchService http = mock(HttpFetchService.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    FeedEntryImporter importer =
        new FeedEntryImporter(
            http,
            new ArticleUrlCanonicalizer(),
            mock(ContentHeaderRepository.class),
            extractor,
            mock(ContentFullTextWriter.class));
    URI article = URI.create("https://example.test/a");
    SyndEntryImpl entry = new SyndEntryImpl();
    entry.setLink(article.toString());
    when(http.resolveRedirect(article))
        .thenReturn(new RedirectResolution.Resolved(article, article));
    when(extractor.extract(entry))
        .thenReturn(
            new TextExtractionOutcome.Skipped(TextExtractionSkipReason.PLAYWRIGHT_DISABLED));

    assertThatThrownBy(() -> importer.importEntry(feed("feed"), entry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be skipped");
  }

  @Test
  void feedEntryImporterDoesNotHideUnexpectedPersistenceFailure() {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository headers = mock(ContentHeaderRepository.class);
    FeedEntryImporter importer =
        new FeedEntryImporter(
            http,
            new ArticleUrlCanonicalizer(),
            headers,
            mock(FeedEntryFullTextExtractor.class),
            mock(ContentFullTextWriter.class));
    URI article = URI.create("https://example.test/a");
    SyndEntryImpl entry = new SyndEntryImpl();
    entry.setLink(article.toString());
    when(http.resolveRedirect(article))
        .thenReturn(new RedirectResolution.Resolved(article, article));
    doThrow(new IllegalStateException("programming defect"))
        .when(headers)
        .insertOrRefreshFetchUrl(any());

    assertThatThrownBy(() -> importer.importEntry(feed("http"), entry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("programming defect");
  }

  @Test
  void probeInterruptedFailurePreservesInterruptInCliMappingPath() {
    ProbeOutcome.Failed interrupted =
        new ProbeOutcome.Failed(
            OperationFailure.of(
                FailureStage.FETCH_ARTICLE,
                FailureKind.INTERRUPTED,
                "https://x",
                "stopped",
                new InterruptedException("stop")));
    assertThat(interrupted.failure().interrupted()).isTrue();
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
  void feedEntryImportServiceAbortsOnEntryFailureWithPartialSummary() throws Exception {
    FeedDocumentService documents = mock(FeedDocumentService.class);
    FeedEntryImporter importer = mock(FeedEntryImporter.class);
    FeedEntryImportService service = new FeedEntryImportService(documents, importer);

    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry first = mock(SyndEntry.class);
    SyndEntry second = mock(SyndEntry.class);
    when(feed.getEntries()).thenReturn(List.of(first, second));
    when(documents.fetch(any())).thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(importer.importEntry(any(), eq(first)))
        .thenReturn(
            new FeedEntryImportOutcome.Inserted(
                new RedirectResolution.Resolved(URI.create("https://a"), URI.create("https://a")),
                Optional.empty()));
    when(importer.importEntry(any(), eq(second)))
        .thenReturn(
            new FeedEntryImportOutcome.Failed(
                OperationFailure.of(
                    FailureStage.PERSIST_HEADER, FailureKind.PERSISTENCE, "x", "fail")));

    FeedImportResult.Failed failed = (FeedImportResult.Failed) service.importEntries(feed("http"));
    assertThat(failed.summary().insertedHeaders()).isEqualTo(1);
    assertThat(failed.summary().entriesSeen()).isEqualTo(2);
    verify(importer).importEntry(any(), eq(first));
    verify(importer).importEntry(any(), eq(second));
  }

  @Test
  void feedReaderStopsWhenThreadInterruptedBeforeImport() {
    FeedRegistrationService registration = mock(FeedRegistrationService.class);
    FeedUrlRepository feeds = mock(FeedUrlRepository.class);
    FeedEntryImportService importService = mock(FeedEntryImportService.class);
    FullTextExtractionService extraction = mock(FullTextExtractionService.class);
    ContentTextFileExportService export = mock(ContentTextFileExportService.class);
    FeedUrl feedUrl = new FeedUrl("f", "https://example.test/rss.xml");
    when(registration.registerFeedUrls(any())).thenReturn(0);
    when(feeds.findActiveForReading()).thenReturn(List.of(feedUrl));
    Thread.currentThread().interrupt();

    FeedReaderService.JobResult result =
        new FeedReaderService(
                propertiesExport(10), registration, feeds, importService, extraction, export)
            .runOnce();

    assertThat(result.stopReason()).contains(JobStopReason.INTERRUPTED);
    verify(importService, never()).importEntries(any());
    verify(extraction, never()).extractPending(100);
  }

  @Test
  void probeDocumentFetcherCoversHttpStatusPlaywrightAndUnexpectedSource() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    ProbeDocumentFetcher fetcher = new ProbeDocumentFetcher(http, playwright, properties(true));

    when(http.get(URI.create("https://a")))
        .thenThrow(new IOException("GET https://a returned HTTP 500"));
    ProbeDocumentFetcher.FetchOutcome.Failed status =
        (ProbeDocumentFetcher.FetchOutcome.Failed)
            fetcher.fetch(
                URI.create("https://a"),
                net.sasasin.sreader.domain.ExtractionPlan.from(FullTextMethod.HTTP),
                "https://a");
    assertThat(status.failure().kind()).isEqualTo(FailureKind.HTTP_STATUS);

    when(playwright.renderPage(any(), eq(false))).thenThrow(new RuntimeException("render"));
    ProbeDocumentFetcher.FetchOutcome.Failed render =
        (ProbeDocumentFetcher.FetchOutcome.Failed)
            fetcher.fetch(
                URI.create("https://a"),
                net.sasasin.sreader.domain.ExtractionPlan.from(FullTextMethod.PLAYWRIGHT),
                "https://a");
    assertThat(render.failure().kind()).isEqualTo(FailureKind.RENDER);

    ProbeDocumentFetcher.FetchOutcome.Failed unexpected =
        (ProbeDocumentFetcher.FetchOutcome.Failed)
            fetcher.fetch(
                URI.create("https://a"),
                net.sasasin.sreader.domain.ExtractionPlan.from(FullTextMethod.FEED),
                "https://a");
    assertThat(unexpected.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);
  }

  @Test
  void probeServiceFeedFetchFailureAndInvalidLinkAndEmptyTitle() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    FeedDocumentService documents = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor feedExtractor = mock(FeedEntryFullTextExtractor.class);
    FullTextProbeService service =
        new FullTextProbeService(
            http, playwright, extractor, documents, picker, feedExtractor, properties(true));

    when(documents.fetch(URI.create("https://feed")))
        .thenReturn(
            new FeedDocumentOutcome.Failed(
                OperationFailure.of(
                    FailureStage.FETCH_FEED, FailureKind.IO, "https://feed", "down")));
    assertThat(
            service.probeFeed(
                URI.create("https://feed"),
                FullTextMethod.HTTP,
                net.sasasin.sreader.domain.FeedEntrySelection.first(),
                Optional.empty()))
        .isInstanceOf(ProbeOutcome.Failed.class);

    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = mock(SyndEntry.class);
    when(entry.getLink()).thenReturn("http://[");
    when(entry.getTitle()).thenReturn(" ");
    when(documents.fetch(URI.create("https://feed")))
        .thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(picker.pick(feed, net.sasasin.sreader.domain.FeedEntrySelection.first(), true))
        .thenReturn(Optional.of(entry));
    ProbeOutcome.Failed badLink =
        (ProbeOutcome.Failed)
            service.probeFeed(
                URI.create("https://feed"),
                FullTextMethod.HTTP,
                net.sasasin.sreader.domain.FeedEntrySelection.first(),
                Optional.empty());
    assertThat(badLink.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);

    URI link = URI.create("https://entry");
    when(entry.getLink()).thenReturn(link.toString());
    when(entry.getTitle()).thenReturn(null);
    when(http.resolveRedirect(link)).thenReturn(new RedirectResolution.Resolved(link, link));
    when(http.get(link))
        .thenReturn(new HttpFetchService.FetchedResource(link, "<html><title></title></html>"));
    when(extractor.extract(any(), any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "body", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));
    ProbeOutcome.Succeeded succeeded =
        (ProbeOutcome.Succeeded)
            service.probeFeed(
                URI.create("https://feed"),
                FullTextMethod.HTTP,
                net.sasasin.sreader.domain.FeedEntrySelection.first(),
                Optional.empty());
    assertThat(succeeded.document().title()).isEmpty();
  }

  @Test
  void feedDocumentFetchedNullEntriesIsEmptyList() {
    SyndFeed feed = mock(SyndFeed.class);
    when(feed.getEntries()).thenReturn(null);
    assertThat(new FeedDocumentOutcome.Fetched(feed).entries()).isEmpty();
  }

  @Test
  void outcomePreconditionsAndOperationFailureFactories() {
    assertThatThrownBy(() -> OutcomePreconditions.requireNonNegative("n", -1))
        .isInstanceOf(IllegalArgumentException.class);
    OperationFailure withCause =
        OperationFailure.of(
            FailureStage.FETCH_ARTICLE, FailureKind.IO, "s", "m", new IOException("cause"));
    assertThat(withCause.cause()).isPresent();
    assertThat(withCause.interrupted()).isFalse();
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

  private static FeedUrl feed(String method) {
    return new FeedUrl(
        "feed",
        "https://example.test/rss.xml",
        FeedStatus.ACTIVE,
        null,
        null,
        null,
        FullTextMethod.fromValue(method));
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

  private FeedReaderProperties propertiesExport(int batch) {
    return new FeedReaderProperties(
        null,
        null,
        null,
        null,
        new FeedReaderProperties.TextExport(true, java.nio.file.Path.of("/tmp"), batch),
        List.of());
  }
}
