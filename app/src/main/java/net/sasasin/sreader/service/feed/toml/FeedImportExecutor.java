package net.sasasin.sreader.service.feed.toml;

import java.util.List;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.repository.FeedUrlRepository;

/** Applies import decisions and owns repository mutations and result aggregation. */
final class FeedImportExecutor {
  private final FeedUrlRepository repository;

  FeedImportExecutor(FeedUrlRepository repository) {
    this.repository = repository;
  }

  void execute(
      FeedImportPlanner.FeedImportDecision decision,
      boolean dryRun,
      Counters counters,
      List<String> conflicts) {
    switch (decision) {
      case FeedImportPlanner.FeedImportDecision.Insert d -> {
        counters.inserted++;
        if (d.feed().status() == FeedStatus.UNSUBSCRIBED) {
          counters.unsubscribed++;
        }
        if (!dryRun) {
          repository.insertFromImport(d.feed().toFeedUrl());
        }
      }
      case FeedImportPlanner.FeedImportDecision.UpdateMethod d -> {
        counters.updated++;
        if (!dryRun) {
          repository.updateFullTextMethod(d.feed().url(), d.feed().fullTextMethod());
        }
      }
      case FeedImportPlanner.FeedImportDecision.Unsubscribe d -> {
        counters.updated++;
        counters.unsubscribed++;
        if (!dryRun) {
          repository.unsubscribe(
              d.feed().url(),
              d.feed().unsubscribeReason(),
              d.feed().unsubscribedAt(),
              d.feed().note());
          repository.updateFullTextMethod(d.feed().url(), d.feed().fullTextMethod());
        }
      }
      case FeedImportPlanner.FeedImportDecision.UpdateUnsubscribedMetadata d -> {
        counters.updated++;
        if (!dryRun) {
          repository.updateUnsubscribedMetadata(
              d.feed().url(),
              d.feed().unsubscribeReason(),
              d.feed().unsubscribedAt(),
              d.feed().note());
          if (d.methodChanged()) {
            repository.updateFullTextMethod(d.feed().url(), d.feed().fullTextMethod());
          }
        }
      }
      case FeedImportPlanner.FeedImportDecision.Resubscribe d -> {
        counters.updated++;
        counters.resubscribed++;
        if (!dryRun) {
          repository.resubscribe(d.feed().url());
          repository.updateFullTextMethod(d.feed().url(), d.feed().fullTextMethod());
        }
      }
      case FeedImportPlanner.FeedImportDecision.Unchanged ignored -> counters.unchanged++;
      case FeedImportPlanner.FeedImportDecision.Conflict d -> {
        counters.conflicts++;
        conflicts.add(
            "feed["
                + d.feed().index()
                + "] "
                + d.feed().url()
                + " is unsubscribed in DB but active in TOML");
      }
    }
  }

  static final class Counters {
    int inserted;
    int updated;
    int unchanged;
    int unsubscribed;
    int resubscribed;
    int conflicts;
  }
}
