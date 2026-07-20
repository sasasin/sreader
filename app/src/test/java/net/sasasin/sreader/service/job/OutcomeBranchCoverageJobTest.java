package net.sasasin.sreader.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.FeedUrlRepository;
import net.sasasin.sreader.service.extraction.FullTextExtractionBatchResult;
import net.sasasin.sreader.service.extraction.FullTextExtractionService;
import net.sasasin.sreader.service.feed.FeedRegistrationService;
import net.sasasin.sreader.service.feed.ingestion.FeedEntryImportService;
import net.sasasin.sreader.service.feed.ingestion.FeedImportResult;
import net.sasasin.sreader.service.feed.ingestion.FeedImportSummary;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.text.ContentTextFileExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Extra branch coverage for P0-3 outcome paths (job). */
class OutcomeBranchCoverageJobTest {

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
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
