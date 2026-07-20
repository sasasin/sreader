package net.sasasin.sreader.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.job.FeedReaderService;
import org.junit.jupiter.api.Test;

class FeedReaderSchedulerTest {

  private static FeedReaderProperties properties(boolean enabled) {
    return new FeedReaderProperties(
        new FeedReaderProperties.Scheduler(enabled, "0 */15 * * * *"),
        new FeedReaderProperties.Job(false),
        new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        null,
        null,
        List.of());
  }

  @Test
  void disabledSchedulerDoesNotRunPeriodicJob() {
    FeedReaderService service = mock(FeedReaderService.class);
    FeedReaderScheduler scheduler = new FeedReaderScheduler(properties(false), service);

    scheduler.runScheduled();

    verify(service, never()).runOnce();
  }

  @Test
  void enabledSchedulerRunsOnce() {
    FeedReaderService service = mock(FeedReaderService.class);
    FeedReaderScheduler scheduler = new FeedReaderScheduler(properties(true), service);

    scheduler.runScheduled();

    verify(service).runOnce();
  }

  @Test
  void runIfIdleRunsOnce() {
    FeedReaderService service = mock(FeedReaderService.class);
    FeedReaderScheduler scheduler = new FeedReaderScheduler(properties(true), service);

    assertThat(scheduler.runIfIdle()).isTrue();
    verify(service).runOnce();
  }

  @Test
  void serviceFailurePropagatesAndClearsRunningFlag() {
    FeedReaderService service = mock(FeedReaderService.class);
    RuntimeException failure = new RuntimeException("boom");
    when(service.runOnce()).thenThrow(failure).thenReturn(null);
    FeedReaderScheduler scheduler = new FeedReaderScheduler(properties(true), service);

    assertThatThrownBy(scheduler::runIfIdle).isSameAs(failure);
    assertThat(scheduler.runIfIdle()).isTrue();
    verify(service, times(2)).runOnce();
  }

  @Test
  void alreadyRunningSkipsSecondTriggerWithoutSleep() throws Exception {
    FeedReaderService service = mock(FeedReaderService.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger concurrentCalls = new AtomicInteger();

    doAnswer(
            invocation -> {
              concurrentCalls.incrementAndGet();
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release latch timed out");
              }
              return null;
            })
        .when(service)
        .runOnce();

    FeedReaderScheduler scheduler = new FeedReaderScheduler(properties(true), service);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> first = executor.submit(scheduler::runIfIdle);

      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(scheduler.runIfIdle()).isFalse();
      assertThat(concurrentCalls.get()).isEqualTo(1);
      verify(service, times(1)).runOnce();

      release.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }
}
