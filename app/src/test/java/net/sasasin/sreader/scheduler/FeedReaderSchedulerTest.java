package net.sasasin.sreader.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.FeedReaderService;
import org.junit.jupiter.api.Test;

class FeedReaderSchedulerTest {

	@Test
	void disabledSchedulerDoesNotRunPeriodicJob() {
		FeedReaderService service = mock(FeedReaderService.class);
		FeedReaderProperties properties = new FeedReaderProperties(
				new FeedReaderProperties.Scheduler(false, "0 */15 * * * *"),
				new FeedReaderProperties.Job(false),
				new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
				List.of());
		FeedReaderScheduler scheduler = new FeedReaderScheduler(properties, service);

		scheduler.runScheduled();

		verify(service, never()).runOnce();
	}

	@Test
	void runIfIdleRunsOnce() {
		FeedReaderService service = mock(FeedReaderService.class);
		FeedReaderProperties properties = new FeedReaderProperties(
				new FeedReaderProperties.Scheduler(true, "0 */15 * * * *"),
				new FeedReaderProperties.Job(false),
				new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
				List.of());
		FeedReaderScheduler scheduler = new FeedReaderScheduler(properties, service);

		assertThat(scheduler.runIfIdle()).isTrue();
		verify(service).runOnce();
	}
}
