package net.sasasin.sreader.runner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import net.sasasin.sreader.service.FeedTomlService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class FeedReaderCommandRunnerTest {

  @Test
  void runOncePropertyTriggersJob() throws Exception {
    FeedReaderProperties properties =
        new FeedReaderProperties(
            new FeedReaderProperties.Scheduler(false, "0 */15 * * * *"),
            new FeedReaderProperties.Job(true),
            new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
            List.of());
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    FeedTomlService feedTomlService = mock(FeedTomlService.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

    new FeedReaderCommandRunner(properties, scheduler, feedTomlService, context).run();

    verify(scheduler).runIfIdle();
    verify(context).close();
  }
}
