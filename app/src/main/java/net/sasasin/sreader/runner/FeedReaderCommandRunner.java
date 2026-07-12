package net.sasasin.sreader.runner;

import java.util.Arrays;
import java.util.List;
import net.sasasin.sreader.cli.PicocliFactory;
import net.sasasin.sreader.cli.SreaderCommand;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class FeedReaderCommandRunner implements CommandLineRunner {

  private static final List<String> SPRING_PROP_PREFIXES =
      List.of("--spring.", "--sreader.", "--server.", "--management.");

  private final FeedReaderProperties properties;
  private final FeedReaderScheduler scheduler;
  private final ConfigurableApplicationContext applicationContext;
  private final SreaderCommand sreaderCommand;
  private final PicocliFactory picocliFactory;

  public FeedReaderCommandRunner(
      FeedReaderProperties properties,
      FeedReaderScheduler scheduler,
      ConfigurableApplicationContext applicationContext,
      SreaderCommand sreaderCommand,
      PicocliFactory picocliFactory) {
    this.properties = properties;
    this.scheduler = scheduler;
    this.applicationContext = applicationContext;
    this.sreaderCommand = sreaderCommand;
    this.picocliFactory = picocliFactory;
  }

  @Override
  public void run(String... args) throws Exception {
    String[] filtered = filterSpringBootOptions(args);
    if (isExplicitCliInvocation(filtered)) {
      CommandLine cli = new CommandLine(sreaderCommand, picocliFactory);
      // picocli default: 2 for usage errors (bad option / missing required).
      // Map execution exceptions (runtime errors inside commands) to 1.
      cli.setExitCodeExceptionMapper(ex -> ex instanceof CommandLine.ParameterException ? 2 : 1);
      int exitCode = cli.execute(filtered);
      exitApplication(exitCode);
      return;
    }

    if (properties.job().runOnce()) {
      scheduler.runIfIdle();
      exitApplication(0);
    }
    // otherwise fall through: application keeps running as daemon
    // (scheduler will trigger periodic jobs per sreader.scheduler.* config)
  }

  /**
   * Remove Spring Boot / sreader property arguments before passing to picocli. Supports usage like:
   * java -jar app.jar --sreader.scheduler.enabled=false feeds import --input feeds.toml
   */
  private String[] filterSpringBootOptions(String[] args) {
    return Arrays.stream(args)
        .filter(a -> SPRING_PROP_PREFIXES.stream().noneMatch(a::startsWith))
        .toArray(String[]::new);
  }

  /**
   * Returns true when the (filtered) args indicate an explicit CLI subcommand or help request.
   * Non-matching cases fall back to the run-once property / normal daemon startup behavior.
   */
  private boolean isExplicitCliInvocation(String[] args) {
    if (args.length == 0) {
      return false;
    }
    for (String a : args) {
      if ("feeds".equals(a)
          || "run-once".equals(a)
          || "probe".equals(a)
          || "--help".equals(a)
          || "-h".equals(a)
          || "-?".equals(a)) {
        return true;
      }
    }
    return false;
  }

  private void exitApplication(int exitCode) {
    int resolvedExitCode = SpringApplication.exit(applicationContext, () -> exitCode);
    if (resolvedExitCode != 0) {
      exitProcess(Math.max(exitCode, resolvedExitCode));
    }
  }

  /** Invokes the JVM exit operation after Spring has closed the application context. */
  void exitProcess(int exitCode) {
    System.exit(exitCode);
  }
}
