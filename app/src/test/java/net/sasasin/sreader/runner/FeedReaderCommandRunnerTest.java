package net.sasasin.sreader.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.sasasin.sreader.cli.SreaderCliExecutor;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

class FeedReaderCommandRunnerTest {

  @Test
  void daemonModesDoNotExecuteCliCloseContextOrExit() throws Exception {
    SreaderCliExecutor cliExecutor = mock(SreaderCliExecutor.class);
    TestRunner noArgs = runner(false, cliExecutor);
    noArgs.run();
    assertThat(noArgs.context.isActive()).isTrue();
    assertThat(noArgs.processExitCode).isNull();
    verifyNoInteractions(cliExecutor);

    TestRunner springPropertiesOnly = runner(false, cliExecutor);
    springPropertiesOnly.run("--spring.profiles.active=test", "--sreader.scheduler.enabled=false");
    assertThat(springPropertiesOnly.context.isActive()).isTrue();
    verifyNoInteractions(cliExecutor);
  }

  @Test
  void propertyRunOnceExecutesSyntheticRunOnceThenClosesWithoutProcessExit() throws Exception {
    SreaderCliExecutor cliExecutor = mock(SreaderCliExecutor.class);
    TestRunner runner = runner(true, cliExecutor);

    runner.run("--server.port=0", "--management.endpoint.enabled=false");

    verify(cliExecutor).execute("run-once");
    assertThat(runner.context.isActive()).isFalse();
    assertThat(runner.processExitCode).isNull();
  }

  @Test
  void explicitCliTakesPriorityOverPropertyRunOnceAndFiltersSpringArguments() throws Exception {
    SreaderCliExecutor cliExecutor = mock(SreaderCliExecutor.class);
    TestRunner runner = runner(true, cliExecutor);

    runner.run("--sreader.job.run-once=true", "--help");

    verify(cliExecutor).execute("--help");
    assertThat(runner.context.isActive()).isFalse();
    assertThat(runner.processExitCode).isNull();
  }

  @Test
  void retainsCliArgumentOrderAndValuesWhenFilteringSpringArguments() throws Exception {
    SreaderCliExecutor cliExecutor = mock(SreaderCliExecutor.class);
    TestRunner runner = runner(false, cliExecutor);

    runner.run("--server.port=0", "feeds", "import", "--input", "/tmp/feeds.toml");

    verify(cliExecutor).execute("feeds", "import", "--input", "/tmp/feeds.toml");
  }

  @Test
  void unknownNonSpringArgumentIsSentToCliInsteadOfStartingDaemon() throws Exception {
    SreaderCliExecutor cliExecutor = mock(SreaderCliExecutor.class);
    TestRunner runner = runner(false, cliExecutor);

    runner.run("no-such-command");

    verify(cliExecutor).execute("no-such-command");
    assertThat(runner.context.isActive()).isFalse();
  }

  @Test
  void nonZeroCliExitClosesContextAndExitsProcess() throws Exception {
    SreaderCliExecutor cliExecutor = mock(SreaderCliExecutor.class);
    when(cliExecutor.execute("no-such-command")).thenReturn(2);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    when(context.getBeansOfType(org.springframework.boot.ExitCodeGenerator.class))
        .thenReturn(Map.of());
    TestRunner runner = runner(false, cliExecutor, context);

    runner.run("no-such-command");

    verify(context).close();
    assertThat(runner.processExitCode).isEqualTo(2);
  }

  private TestRunner runner(boolean runOnce, SreaderCliExecutor cliExecutor) {
    GenericApplicationContext context = new GenericApplicationContext();
    context.refresh();
    return runner(runOnce, cliExecutor, context);
  }

  private TestRunner runner(
      boolean runOnce, SreaderCliExecutor cliExecutor, ConfigurableApplicationContext context) {
    return new TestRunner(properties(runOnce), context, cliExecutor);
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

  private static final class TestRunner extends FeedReaderCommandRunner {
    private final ConfigurableApplicationContext context;
    private Integer processExitCode;

    TestRunner(
        FeedReaderProperties properties,
        ConfigurableApplicationContext context,
        SreaderCliExecutor cliExecutor) {
      super(properties, context, cliExecutor);
      this.context = context;
    }

    @Override
    void exitProcess(int exitCode) {
      processExitCode = exitCode;
    }
  }
}
