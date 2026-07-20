package net.sasasin.sreader.service.feed.ingestion;

import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.outcome.BatchStopReason;
import net.sasasin.sreader.service.outcome.OutcomePreconditions;

/** Aggregated feed-import results across active feeds in one job run. */
public record FeedImportJobSummary(
    int completedFeeds,
    int failedFeeds,
    FeedImportSummary entries,
    Optional<BatchStopReason> stopReason) {

  public FeedImportJobSummary {
    completedFeeds = OutcomePreconditions.requireNonNegative("completedFeeds", completedFeeds);
    failedFeeds = OutcomePreconditions.requireNonNegative("failedFeeds", failedFeeds);
    Objects.requireNonNull(entries, "entries must not be null");
    stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
  }

  public static FeedImportJobSummary empty() {
    return new FeedImportJobSummary(0, 0, FeedImportSummary.empty(), Optional.empty());
  }
}
