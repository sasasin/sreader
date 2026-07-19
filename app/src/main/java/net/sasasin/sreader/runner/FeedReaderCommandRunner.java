package net.sasasin.sreader.runner;

import java.util.Arrays;
import java.util.List;
import net.sasasin.sreader.cli.SreaderCliExecutor;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FeedReaderCommandRunner implements CommandLineRunner {

  private static final List<String> SPRING_PROP_PREFIXES =
      List.of("--spring.", "--sreader.", "--server.", "--management.");

  private final FeedReaderProperties properties;
  private final ConfigurableApplicationContext applicationContext;
  private final SreaderCliExecutor cliExecutor;

  public FeedReaderCommandRunner(
      FeedReaderProperties properties,
      ConfigurableApplicationContext applicationContext,
      SreaderCliExecutor cliExecutor) {
    this.properties = properties;
    this.applicationContext = applicationContext;
    this.cliExecutor = cliExecutor;
  }

  @Override
  public void run(String... args) throws Exception {
    String[] filtered = filterSpringBootOptions(args);
    if (filtered.length > 0) {
      exitApplication(cliExecutor.execute(filtered));
    } else if (properties.job().runOnce()) {
      exitApplication(cliExecutor.execute("run-once"));
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
