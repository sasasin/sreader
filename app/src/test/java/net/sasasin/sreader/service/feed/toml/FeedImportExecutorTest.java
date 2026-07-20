package net.sasasin.sreader.service.feed.toml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.junit.jupiter.api.Test;

class FeedImportExecutorTest {

  private final FeedUrlRepository repository = mock(FeedUrlRepository.class);
  private final FeedImportExecutor executor = new FeedImportExecutor(repository);

  @Test
  void dryRunAggregatesInsertWithoutMutation() {
    FeedImportExecutor.Counters counters = new FeedImportExecutor.Counters();
    executor.execute(
        new FeedImportPlanner.FeedImportDecision.Insert(
            new FeedTomlService.ImportFeed(
                1,
                "https://example.test/feed.xml",
                FeedStatus.UNSUBSCRIBED,
                UnsubscribeReason.OTHER,
                null,
                null,
                FullTextMethod.HTTP)),
        true,
        counters,
        new ArrayList<>());

    assertThat(counters.inserted).isEqualTo(1);
    assertThat(counters.unsubscribed).isEqualTo(1);
    verifyNoInteractions(repository);
  }

  @Test
  void conflictProducesTheEstablishedMessageWithoutMutation() {
    FeedImportExecutor.Counters counters = new FeedImportExecutor.Counters();
    ArrayList<String> conflicts = new ArrayList<>();
    executor.execute(
        new FeedImportPlanner.FeedImportDecision.Conflict(
            new FeedTomlService.ImportFeed(
                2,
                "https://example.test/feed.xml",
                FeedStatus.ACTIVE,
                null,
                null,
                null,
                FullTextMethod.HTTP)),
        false,
        counters,
        conflicts);

    assertThat(counters.conflicts).isEqualTo(1);
    assertThat(conflicts)
        .containsExactly(
            "feed[2] https://example.test/feed.xml is unsubscribed in DB but active in TOML");
    verifyNoInteractions(repository);
  }
}
