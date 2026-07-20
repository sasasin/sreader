package net.sasasin.sreader.service;

import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeedReaderService {

  private static final Logger logger = LoggerFactory.getLogger(FeedReaderService.class);

  private final FeedReaderProperties properties;
  private final FeedRegistrationService feedRegistrationService;
  private final FeedUrlRepository feedUrlRepository;
  private final FeedEntryImportService feedEntryImportService;
  private final FullTextExtractionService fullTextExtractionService;
  private final ContentTextFileExportService contentTextFileExportService;

  public FeedReaderService(
      FeedReaderProperties properties,
      FeedRegistrationService feedRegistrationService,
      FeedUrlRepository feedUrlRepository,
      FeedEntryImportService feedEntryImportService,
      FullTextExtractionService fullTextExtractionService,
      ContentTextFileExportService contentTextFileExportService) {
    this.properties = properties;
    this.feedRegistrationService = feedRegistrationService;
    this.feedUrlRepository = feedUrlRepository;
    this.feedEntryImportService = feedEntryImportService;
    this.fullTextExtractionService = fullTextExtractionService;
    this.contentTextFileExportService = contentTextFileExportService;
  }

  public JobResult runOnce() {
    int seeded = feedRegistrationService.registerFeedUrls(properties.seedFeedUrls());

    int completedFeeds = 0;
    int failedFeeds = 0;
    FeedImportSummary entrySummary = FeedImportSummary.empty();
    Optional<BatchStopReason> feedStopReason = Optional.empty();

    for (FeedUrl feedUrl : feedUrlRepository.findActiveForReading()) {
      if (Thread.currentThread().isInterrupted()) {
        feedStopReason = Optional.of(BatchStopReason.INTERRUPTED);
        break;
      }
      FeedImportResult importResult = feedEntryImportService.importEntries(feedUrl);
      entrySummary = entrySummary.plus(importResult.summary());
      switch (importResult) {
        case FeedImportResult.Completed ignored -> completedFeeds++;
        case FeedImportResult.Failed failed -> {
          failedFeeds++;
          OperationFailure failure = failed.failure();
          if (failure.cause().isPresent()) {
            logger.error(
                "Failed to import feed {} stage={} kind={} message={}",
                feedUrl.url(),
                failure.stage(),
                failure.kind(),
                failure.message(),
                failure.cause().get());
          } else {
            logger.error(
                "Failed to import feed {} stage={} kind={} message={}",
                feedUrl.url(),
                failure.stage(),
                failure.kind(),
                failure.message());
          }
          if (failure.interrupted()) {
            feedStopReason = Optional.of(BatchStopReason.INTERRUPTED);
            FeedImportJobSummary feedImport =
                new FeedImportJobSummary(completedFeeds, failedFeeds, entrySummary, feedStopReason);
            JobResult partial =
                new JobResult(
                    seeded,
                    feedImport,
                    FullTextExtractionBatchResult.empty(),
                    0,
                    Optional.of(JobStopReason.INTERRUPTED));
            logger.warn("Feed reader job interrupted during feed import: {}", partial);
            return partial;
          }
        }
      }
    }

    FeedImportJobSummary feedImport =
        new FeedImportJobSummary(completedFeeds, failedFeeds, entrySummary, feedStopReason);
    if (feedStopReason.isPresent()) {
      JobResult partial =
          new JobResult(
              seeded,
              feedImport,
              FullTextExtractionBatchResult.empty(),
              0,
              Optional.of(JobStopReason.INTERRUPTED));
      logger.warn("Feed reader job interrupted after feed import loop: {}", partial);
      return partial;
    }

    FullTextExtractionBatchResult fullTextExtraction =
        fullTextExtractionService.extractPending(100);
    if (fullTextExtraction.stopReason().isPresent()) {
      JobResult partial =
          new JobResult(
              seeded, feedImport, fullTextExtraction, 0, Optional.of(JobStopReason.INTERRUPTED));
      logger.warn("Feed reader job interrupted during full text extraction: {}", partial);
      return partial;
    }

    int textFilesExported =
        contentTextFileExportService.exportPending(properties.textExport().batchSize());
    JobResult result =
        new JobResult(seeded, feedImport, fullTextExtraction, textFilesExported, Optional.empty());
    logger.info("Feed reader job finished: {}", result);
    return result;
  }

  public record JobResult(
      int feedUrlsInserted,
      FeedImportJobSummary feedImport,
      FullTextExtractionBatchResult fullTextExtraction,
      int textFilesExported,
      Optional<JobStopReason> stopReason) {

    public JobResult {
      feedUrlsInserted =
          OutcomePreconditions.requireNonNegative("feedUrlsInserted", feedUrlsInserted);
      Objects.requireNonNull(feedImport, "feedImport must not be null");
      Objects.requireNonNull(fullTextExtraction, "fullTextExtraction must not be null");
      textFilesExported =
          OutcomePreconditions.requireNonNegative("textFilesExported", textFilesExported);
      stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
    }
  }
}
