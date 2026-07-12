package net.sasasin.sreader.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.sasasin.sreader.cli.FeedExportCommand;
import net.sasasin.sreader.cli.FeedImportCommand;
import net.sasasin.sreader.cli.FeedsDiscoverCommand;
import net.sasasin.sreader.cli.PicocliFactory;
import net.sasasin.sreader.cli.ProbeArticleCommand;
import net.sasasin.sreader.cli.ProbeCommand;
import net.sasasin.sreader.cli.ProbeFeedCommand;
import net.sasasin.sreader.cli.RunOnceCommand;
import net.sasasin.sreader.cli.SreaderCommand;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import net.sasasin.sreader.service.FeedDiscoveryService;
import net.sasasin.sreader.service.FeedTomlService;
import net.sasasin.sreader.service.FullTextProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

class FeedReaderCommandRunnerTest {

  @Test
  void daemonModesDoNotRunJobsCloseContextOrExit() throws Exception {
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    TestRunner noArgs = runner(false, scheduler);
    noArgs.run();
    assertThat(noArgs.context.isActive()).isTrue();
    assertThat(noArgs.processExitCode).isNull();

    TestRunner springPropertiesOnly = runner(false, scheduler);
    springPropertiesOnly.run("--spring.profiles.active=test", "--sreader.scheduler.enabled=false");
    assertThat(springPropertiesOnly.context.isActive()).isTrue();
    verifyNoInteractions(scheduler);
  }

  @Test
  void propertyRunOnceRunsSchedulerThenClosesWithoutProcessExit() throws Exception {
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    TestRunner runner = runner(true, scheduler);
    runner.run("--server.port=0", "--management.endpoint.enabled=false");
    verify(scheduler).runIfIdle();
    assertThat(runner.context.isActive()).isFalse();
    assertThat(runner.processExitCode).isNull();
  }

  @Test
  void explicitCliTakesPriorityOverPropertyRunOnceAndFiltersSpringArguments() throws Exception {
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    TestRunner runner = runner(true, scheduler);
    runner.run("--sreader.job.run-once=true", "--help");
    verifyNoInteractions(scheduler);
    assertThat(runner.context.isActive()).isFalse();
    assertThat(runner.processExitCode).isNull();
  }

  @Test
  void helpTokensAndSubcommandsAreExplicitCliInvocations() throws Exception {
    for (String command : List.of("--help", "-h", "-?", "feeds", "probe")) {
      FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
      TestRunner runner = runner(true, scheduler);
      if (command.startsWith("-")) {
        runner.run(command);
      } else {
        runner.run(command, "--help");
      }
      verifyNoInteractions(scheduler);
      assertThat(runner.context.isActive()).isFalse();
      if ("-?".equals(command)) {
        assertThat(runner.processExitCode).isEqualTo(2);
      } else {
        assertThat(runner.processExitCode).isNull();
      }
    }
  }

  @Test
  void usageErrorsAndRuntimeExceptionsExitWithNonZeroCode() throws Exception {
    FeedReaderScheduler usageScheduler = mock(FeedReaderScheduler.class);
    ConfigurableApplicationContext usageContext = mock(ConfigurableApplicationContext.class);
    noExitCodeBeans(usageContext);
    TestRunner usage = runner(false, usageScheduler, usageContext);
    usage.run("feeds", "--unknown-option");
    verify(usageContext).close();
    assertThat(usage.processExitCode).isEqualTo(2);

    FeedReaderScheduler failingScheduler = mock(FeedReaderScheduler.class);
    doThrow(new RuntimeException("boom")).when(failingScheduler).runIfIdle();
    ConfigurableApplicationContext failingContext = mock(ConfigurableApplicationContext.class);
    noExitCodeBeans(failingContext);
    TestRunner failing = runner(false, failingScheduler, failingContext);
    failing.run("run-once");
    verify(failingContext).close();
    assertThat(failing.processExitCode).isEqualTo(1);
  }

  private TestRunner runner(boolean runOnce, FeedReaderScheduler scheduler) {
    GenericApplicationContext context = new GenericApplicationContext();
    context.refresh();
    return runner(runOnce, scheduler, context);
  }

  private TestRunner runner(
      boolean runOnce, FeedReaderScheduler scheduler, ConfigurableApplicationContext context) {
    return new TestRunner(
        properties(runOnce), scheduler, context, new SreaderCommand(), new TestFactory(scheduler));
  }

  private FeedReaderProperties properties(boolean runOnce) {
    return new FeedReaderProperties(
        new FeedReaderProperties.Scheduler(false, "0 */15 * * * *"),
        new FeedReaderProperties.Job(runOnce),
        new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        null,
        null,
        List.of());
  }

  private void noExitCodeBeans(ConfigurableApplicationContext context) {
    when(context.getBeansOfType(org.springframework.boot.ExitCodeGenerator.class))
        .thenReturn(Map.of());
  }

  private static final class TestRunner extends FeedReaderCommandRunner {
    private final ConfigurableApplicationContext context;
    private Integer processExitCode;

    TestRunner(
        FeedReaderProperties properties,
        FeedReaderScheduler scheduler,
        ConfigurableApplicationContext context,
        SreaderCommand sreaderCommand,
        PicocliFactory picocliFactory) {
      super(properties, scheduler, context, sreaderCommand, picocliFactory);
      this.context = context;
    }

    @Override
    void exitProcess(int exitCode) {
      processExitCode = exitCode;
    }
  }

  private static final class TestFactory extends PicocliFactory {
    private final FeedReaderScheduler scheduler;
    private final FeedTomlService feedTomlService = mock(FeedTomlService.class);
    private final FeedDiscoveryService feedDiscoveryService = mock(FeedDiscoveryService.class);
    private final FullTextProbeService fullTextProbeService = mock(FullTextProbeService.class);

    TestFactory(FeedReaderScheduler scheduler) {
      super(null);
      this.scheduler = scheduler;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
      if (cls == RunOnceCommand.class) {
        return cls.cast(new RunOnceCommand(scheduler));
      }
      if (cls == ProbeCommand.class) {
        return cls.cast(new ProbeCommand());
      }
      if (cls == ProbeArticleCommand.class) {
        return cls.cast(new ProbeArticleCommand(fullTextProbeService));
      }
      if (cls == ProbeFeedCommand.class) {
        return cls.cast(new ProbeFeedCommand(fullTextProbeService));
      }
      if (cls == FeedsDiscoverCommand.class) {
        return cls.cast(new FeedsDiscoverCommand(feedDiscoveryService));
      }
      if (cls == FeedImportCommand.class) {
        return cls.cast(new FeedImportCommand(feedTomlService));
      }
      if (cls == FeedExportCommand.class) {
        return cls.cast(new FeedExportCommand(feedTomlService));
      }
      return super.create(cls);
    }
  }
}
