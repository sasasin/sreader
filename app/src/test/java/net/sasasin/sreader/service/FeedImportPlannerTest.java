package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;

class FeedImportPlannerTest {

  private final FeedImportPlanner planner = new FeedImportPlanner();

  @Test
  void plansAllStateTransitionsWithoutRepositoryAccess() {
    assertThat(planner.plan(null, desired(FeedStatus.ACTIVE.value(), "http"), false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Insert.class);
    assertThat(planner.plan(active("http"), desired(FeedStatus.ACTIVE.value(), "http"), false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Unchanged.class);
    assertThat(planner.plan(active("http"), desired(FeedStatus.ACTIVE.value(), "feed"), false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.UpdateMethod.class);
    assertThat(
            planner.plan(active("http"), desired(FeedStatus.UNSUBSCRIBED.value(), "feed"), false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Unsubscribe.class);
    assertThat(planner.plan(unsubscribed(), desired(FeedStatus.ACTIVE.value(), "http"), false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Conflict.class);
    assertThat(planner.plan(unsubscribed(), desired(FeedStatus.ACTIVE.value(), "http"), true))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.Resubscribe.class);
  }

  @Test
  void plansUnsubscribedMetadataChangeSeparately() {
    FeedTomlService.ImportFeed desired =
        new FeedTomlService.ImportFeed(
            1,
            "https://example.test/feed.xml",
            FeedStatus.UNSUBSCRIBED.value(),
            "site_closed",
            OffsetDateTime.parse("2026-01-01T00:00Z"),
            "note",
            "http");
    assertThat(planner.plan(unsubscribed(), desired, false))
        .isInstanceOf(FeedImportPlanner.FeedImportDecision.UpdateUnsubscribedMetadata.class);
  }

  private FeedTomlService.ImportFeed desired(String status, String method) {
    return new FeedTomlService.ImportFeed(
        1, "https://example.test/feed.xml", status, "other", null, null, method);
  }

  private FeedUrl active(String method) {
    return new FeedUrl(
        "id",
        "https://example.test/feed.xml",
        FeedStatus.ACTIVE.value(),
        null,
        null,
        null,
        FullTextMethod.fromValue(method));
  }

  private FeedUrl unsubscribed() {
    return new FeedUrl(
        "id",
        "https://example.test/feed.xml",
        FeedStatus.UNSUBSCRIBED.value(),
        "other",
        null,
        null,
        FullTextMethod.HTTP);
  }
}
