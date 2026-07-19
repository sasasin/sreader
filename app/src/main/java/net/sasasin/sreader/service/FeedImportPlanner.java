package net.sasasin.sreader.service;

import java.util.Objects;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;

/** Pure state-transition policy for a single imported feed. */
final class FeedImportPlanner {
  FeedImportDecision plan(
      FeedUrl current, FeedTomlService.ImportFeed desired, boolean resubscribe) {
    if (current == null) {
      return new FeedImportDecision.Insert(desired);
    }
    String currentMethod =
        current.fullTextMethod() == null
            ? FullTextMethod.HTTP.value()
            : current.fullTextMethod().value();
    boolean methodChanged = !currentMethod.equals(desired.fullTextMethod());
    if (FeedStatus.ACTIVE.value().equals(current.status())
        && FeedStatus.ACTIVE.value().equals(desired.status())) {
      return methodChanged
          ? new FeedImportDecision.UpdateMethod(desired)
          : new FeedImportDecision.Unchanged();
    }
    if (FeedStatus.ACTIVE.value().equals(current.status())) {
      return new FeedImportDecision.Unsubscribe(desired);
    }
    if (FeedStatus.UNSUBSCRIBED.value().equals(desired.status())) {
      boolean metadataChanged =
          !Objects.equals(current.unsubscribeReason(), desired.unsubscribeReason())
              || !Objects.equals(current.unsubscribedAt(), desired.unsubscribedAt())
              || !Objects.equals(current.note(), desired.note());
      return metadataChanged || methodChanged
          ? new FeedImportDecision.UpdateUnsubscribedMetadata(desired, methodChanged)
          : new FeedImportDecision.Unchanged();
    }
    return resubscribe
        ? new FeedImportDecision.Resubscribe(desired)
        : new FeedImportDecision.Conflict(desired);
  }

  sealed interface FeedImportDecision
      permits FeedImportDecision.Insert,
          FeedImportDecision.UpdateMethod,
          FeedImportDecision.Unsubscribe,
          FeedImportDecision.UpdateUnsubscribedMetadata,
          FeedImportDecision.Resubscribe,
          FeedImportDecision.Unchanged,
          FeedImportDecision.Conflict {
    record Insert(FeedTomlService.ImportFeed feed) implements FeedImportDecision {}

    record UpdateMethod(FeedTomlService.ImportFeed feed) implements FeedImportDecision {}

    record Unsubscribe(FeedTomlService.ImportFeed feed) implements FeedImportDecision {}

    record UpdateUnsubscribedMetadata(FeedTomlService.ImportFeed feed, boolean methodChanged)
        implements FeedImportDecision {}

    record Resubscribe(FeedTomlService.ImportFeed feed) implements FeedImportDecision {}

    record Unchanged() implements FeedImportDecision {}

    record Conflict(FeedTomlService.ImportFeed feed) implements FeedImportDecision {}
  }
}
