package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;
import org.junit.jupiter.api.Test;

class FeedImportPlannerTest {

  private final FeedImportPlanner planner = new FeedImportPlanner();

  @Test
  void plansAllStateTransitionsWithoutRepositoryAccess() {
    assertThat(planner.plan(null, desired(FeedStatus.ACTIVE, FullTextMethod.HTTP), false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Insert.class);
    assertThat(
            planner.plan(
                active(FullTextMethod.HTTP),
                desired(FeedStatus.ACTIVE, FullTextMethod.HTTP),
                false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Unchanged.class);
    assertThat(
            planner.plan(
                active(FullTextMethod.HTTP),
                desired(FeedStatus.ACTIVE, FullTextMethod.FEED),
                false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.UpdateMethod.class);
    assertThat(
            planner.plan(
                active(FullTextMethod.HTTP),
                desired(FeedStatus.UNSUBSCRIBED, FullTextMethod.FEED),
                false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Unsubscribe.class);
    assertThat(planner.plan(unsubscribed(), desired(FeedStatus.ACTIVE, FullTextMethod.HTTP), false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Conflict.class);
    assertThat(planner.plan(unsubscribed(), desired(FeedStatus.ACTIVE, FullTextMethod.HTTP), true))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Resubscribe.class);
  }

  @Test
  void plansUnsubscribedMetadataChangeSeparately() {
    FeedTomlService.ImportFeed desired =
        new FeedTomlService.ImportFeed(
            1,
            "https://example.test/feed.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.SITE_CLOSED,
            OffsetDateTime.parse("2026-01-01T00:00Z"),
            "note",
            FullTextMethod.HTTP);
    assertThat(planner.plan(unsubscribed(), desired, false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.UpdateUnsubscribedMetadata.class);
  }

  private FeedTomlService.ImportFeed desired(FeedStatus status, FullTextMethod method) {
    UnsubscribeReason reason = status == FeedStatus.UNSUBSCRIBED ? UnsubscribeReason.OTHER : null;
    return new FeedTomlService.ImportFeed(
        1, "https://example.test/feed.xml", status, reason, null, null, method);
  }

  private FeedUrl active(FullTextMethod method) {
    return new FeedUrl(
        "id", "https://example.test/feed.xml", FeedStatus.ACTIVE, null, null, null, method);
  }

  private FeedUrl unsubscribed() {
    return new FeedUrl(
        "id",
        "https://example.test/feed.xml",
        FeedStatus.UNSUBSCRIBED,
        UnsubscribeReason.OTHER,
        null,
        null,
        FullTextMethod.HTTP);
  }
}
