package net.sasasin.sreader.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.FeedUrlRepository;
import net.sasasin.sreader.service.extraction.FullTextExtractionBatchResult;
import net.sasasin.sreader.service.extraction.FullTextExtractionService;
import net.sasasin.sreader.service.feed.FeedRegistrationService;
import net.sasasin.sreader.service.feed.ingestion.FeedEntryImportService;
import net.sasasin.sreader.service.feed.ingestion.FeedImportResult;
import net.sasasin.sreader.service.feed.ingestion.FeedImportSummary;
import net.sasasin.sreader.service.outcome.BatchStopReason;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.text.ContentTextFileExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FeedReaderServiceTest {

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void runOnceCallsTextExportAfterFullTextExtraction() {
    FeedRegistrationService feedRegistrationService = mock(FeedRegistrationService.class);
    FeedUrlRepository feedUrlRepository = mock(FeedUrlRepository.class);
    FeedEntryImportService feedEntryImportService = mock(FeedEntryImportService.class);
    FullTextExtractionService fullTextExtractionService = mock(FullTextExtractionService.class);
    ContentTextFileExportService exportService = mock(ContentTextFileExportService.class);
    FeedReaderProperties properties =
        new FeedReaderProperties(
            null,
            null,
            null,
            null,
            new FeedReaderProperties.TextExport(true, Path.of("/tmp/sreader-test"), 7),
            List.of("https://example.test/rss.xml"));
    FeedUrl feedUrl = new FeedUrl("feed", "https://example.test/rss.xml");
    when(feedRegistrationService.registerFeedUrls(properties.seedFeedUrls())).thenReturn(1);
    when(feedUrlRepository.findActiveForReading()).thenReturn(List.of(feedUrl));
    when(feedEntryImportService.importEntries(feedUrl))
        .thenReturn(new FeedImportResult.Completed(new FeedImportSummary(2, 2, 0, 0, 0, 0, 0, 0)));
    when(fullTextExtractionService.extractPending(100))
        .thenReturn(new FullTextExtractionBatchResult(5, 3, 1, 1, 0, 0, Optional.empty()));
    when(exportService.exportPending(7)).thenReturn(4);
    FeedReaderService service =
        new FeedReaderService(
            properties,
            feedRegistrationService,
            feedUrlRepository,
            feedEntryImportService,
            fullTextExtractionService,
            exportService);

    FeedReaderService.JobResult result = service.runOnce();

    assertThat(result.feedUrlsInserted()).isEqualTo(1);
    assertThat(result.feedImport().completedFeeds()).isEqualTo(1);
    assertThat(result.feedImport().failedFeeds()).isZero();
    assertThat(result.feedImport().entries().insertedHeaders()).isEqualTo(2);
    assertThat(result.fullTextExtraction().inserted()).isEqualTo(3);
    assertThat(result.fullTextExtraction().alreadyPresent()).isEqualTo(1);
    assertThat(result.fullTextExtraction().noContent()).isEqualTo(1);
    assertThat(result.textFilesExported()).isEqualTo(4);
    assertThat(result.stopReason()).isEmpty();
    verify(fullTextExtractionService).extractPending(100);
    verify(exportService).exportPending(7);
  }

  @Test
  void emptyFeedStillCountsAsCompleted() {
    FeedRegistrationService registration = mock(FeedRegistrationService.class);
    FeedUrlRepository feeds = mock(FeedUrlRepository.class);
    FeedEntryImportService importService = mock(FeedEntryImportService.class);
    FullTextExtractionService extraction = mock(FullTextExtractionService.class);
    ContentTextFileExportService export = mock(ContentTextFileExportService.class);
    FeedUrl feedUrl = new FeedUrl("feed", "https://example.test/rss.xml");
    when(registration.registerFeedUrls(anyList())).thenReturn(0);
    when(feeds.findActiveForReading()).thenReturn(List.of(feedUrl));
    when(importService.importEntries(feedUrl))
        .thenReturn(new FeedImportResult.Completed(FeedImportSummary.empty()));
    when(extraction.extractPending(100)).thenReturn(FullTextExtractionBatchResult.empty());
    when(export.exportPending(10)).thenReturn(0);

    FeedReaderService service =
        new FeedReaderService(
            properties(10), registration, feeds, importService, extraction, export);

    FeedReaderService.JobResult result = service.runOnce();
    assertThat(result.feedImport().completedFeeds()).isEqualTo(1);
    assertThat(result.feedImport().failedFeeds()).isZero();
  }

  @Test
  void feedFetchFailureIncrementsFailedFeedsAndContinues() {
    FeedRegistrationService registration = mock(FeedRegistrationService.class);
    FeedUrlRepository feeds = mock(FeedUrlRepository.class);
    FeedEntryImportService importService = mock(FeedEntryImportService.class);
    FullTextExtractionService extraction = mock(FullTextExtractionService.class);
    ContentTextFileExportService export = mock(ContentTextFileExportService.class);
    FeedUrl failed = new FeedUrl("f1", "https://example.test/bad.xml");
    FeedUrl ok = new FeedUrl("f2", "https://example.test/ok.xml");
    when(registration.registerFeedUrls(org.mockito.ArgumentMatchers.anyList())).thenReturn(0);
    when(feeds.findActiveForReading()).thenReturn(List.of(failed, ok));
    when(importService.importEntries(failed))
        .thenReturn(
            new FeedImportResult.Failed(
                FeedImportSummary.empty(),
                OperationFailure.of(
                    FailureStage.FETCH_FEED, FailureKind.IO, failed.url(), "down")));
    when(importService.importEntries(ok))
        .thenReturn(new FeedImportResult.Completed(new FeedImportSummary(1, 1, 0, 0, 0, 0, 0, 0)));
    when(extraction.extractPending(100)).thenReturn(FullTextExtractionBatchResult.empty());
    when(export.exportPending(10)).thenReturn(0);

    FeedReaderService service =
        new FeedReaderService(
            properties(10), registration, feeds, importService, extraction, export);

    FeedReaderService.JobResult result = service.runOnce();
    assertThat(result.feedImport().completedFeeds()).isEqualTo(1);
    assertThat(result.feedImport().failedFeeds()).isEqualTo(1);
    assertThat(result.feedImport().entries().insertedHeaders()).isEqualTo(1);
    verify(extraction).extractPending(100);
  }

  @Test
  void feedImportInterruptStopsLaterPhases() {
    FeedRegistrationService registration = mock(FeedRegistrationService.class);
    FeedUrlRepository feeds = mock(FeedUrlRepository.class);
    FeedEntryImportService importService = mock(FeedEntryImportService.class);
    FullTextExtractionService extraction = mock(FullTextExtractionService.class);
    ContentTextFileExportService export = mock(ContentTextFileExportService.class);
    FeedUrl feedUrl = new FeedUrl("feed", "https://example.test/rss.xml");
    when(registration.registerFeedUrls(anyList())).thenReturn(0);
    when(feeds.findActiveForReading()).thenReturn(List.of(feedUrl));
    when(importService.importEntries(feedUrl))
        .thenReturn(
            new FeedImportResult.Failed(
                new FeedImportSummary(1, 0, 0, 0, 0, 0, 0, 0),
                OperationFailure.of(
                    FailureStage.FETCH_FEED,
                    FailureKind.INTERRUPTED,
                    feedUrl.url(),
                    "interrupted")));

    FeedReaderService service =
        new FeedReaderService(
            properties(10), registration, feeds, importService, extraction, export);

    FeedReaderService.JobResult result = service.runOnce();
    assertThat(result.stopReason()).contains(JobStopReason.INTERRUPTED);
    assertThat(result.feedImport().failedFeeds()).isEqualTo(1);
    assertThat(result.textFilesExported()).isZero();
    verify(extraction, never()).extractPending(100);
    verify(export, never()).exportPending(10);
  }

  @Test
  void fullTextInterruptSkipsTextExport() {
    FeedRegistrationService registration = mock(FeedRegistrationService.class);
    FeedUrlRepository feeds = mock(FeedUrlRepository.class);
    FeedEntryImportService importService = mock(FeedEntryImportService.class);
    FullTextExtractionService extraction = mock(FullTextExtractionService.class);
    ContentTextFileExportService export = mock(ContentTextFileExportService.class);
    when(registration.registerFeedUrls(anyList())).thenReturn(0);
    when(feeds.findActiveForReading()).thenReturn(List.of());
    when(extraction.extractPending(100))
        .thenReturn(
            new FullTextExtractionBatchResult(
                2, 0, 0, 0, 0, 1, Optional.of(BatchStopReason.INTERRUPTED)));

    FeedReaderService service =
        new FeedReaderService(
            properties(10), registration, feeds, importService, extraction, export);

    FeedReaderService.JobResult result = service.runOnce();
    assertThat(result.stopReason()).contains(JobStopReason.INTERRUPTED);
    assertThat(result.fullTextExtraction().failed()).isEqualTo(1);
    verify(export, never()).exportPending(10);
  }

  private FeedReaderProperties properties(int exportBatch) {
    return new FeedReaderProperties(
        null,
        null,
        null,
        null,
        new FeedReaderProperties.TextExport(true, Path.of("/tmp/sreader-test"), exportBatch),
        List.of());
  }
}
