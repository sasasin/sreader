package net.sasasin.sreader.service.feed.ingestion;

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

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.article.ArticleUrlCanonicalizer;
import net.sasasin.sreader.service.extraction.ContentFullTextWriteOutcome;
import net.sasasin.sreader.service.extraction.ContentFullTextWriter;
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionSource;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.extraction.NoContentReason;
import net.sasasin.sreader.service.extraction.TextExtractionOutcome;
import net.sasasin.sreader.service.extraction.TextExtractionSkipReason;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.RedirectResolution;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** Extra branch coverage for P0-3 outcome paths (ingestion). */
class OutcomeBranchCoverageIngestionTest {

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
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
  void feedDocumentFetchedNullEntriesIsEmptyList() {
    SyndFeed feed = mock(SyndFeed.class);
    when(feed.getEntries()).thenReturn(null);
    assertThat(new FeedDocumentOutcome.Fetched(feed).entries()).isEmpty();
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
}
