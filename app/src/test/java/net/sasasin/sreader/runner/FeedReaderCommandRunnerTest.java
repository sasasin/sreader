package net.sasasin.sreader.runner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import net.sasasin.sreader.cli.PicocliFactory;
import net.sasasin.sreader.cli.SreaderCommand;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.scheduler.FeedReaderScheduler;
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
            null,
            null,
            List.of());
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    SreaderCommand sreaderCommand = mock(SreaderCommand.class);
    PicocliFactory picocliFactory = mock(PicocliFactory.class);

    new FeedReaderCommandRunner(properties, scheduler, context, sreaderCommand, picocliFactory)
        .run();

    verify(scheduler).runIfIdle();
    verify(context).close();
  }
}
