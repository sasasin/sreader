package net.sasasin.sreader.service.feed.ingestion;

import java.util.Objects;
import net.sasasin.sreader.service.outcome.OperationFailure;

/** Result of importing all entries from one feed. */
public sealed interface FeedImportResult
    permits FeedImportResult.Completed, FeedImportResult.Failed {

  FeedImportSummary summary();

  record Completed(FeedImportSummary summary) implements FeedImportResult {
    public Completed {
      Objects.requireNonNull(summary, "summary must not be null");
    }
  }

  record Failed(FeedImportSummary partialSummary, OperationFailure failure)
      implements FeedImportResult {
    public Failed {
      Objects.requireNonNull(partialSummary, "partialSummary must not be null");
      Objects.requireNonNull(failure, "failure must not be null");
    }

    @Override
    public FeedImportSummary summary() {
      return partialSummary;
    }
  }
}
