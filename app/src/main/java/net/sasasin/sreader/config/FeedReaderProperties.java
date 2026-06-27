package net.sasasin.sreader.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sreader")
public record FeedReaderProperties(
    Scheduler scheduler,
    Job job,
    Http http,
    Playwright playwright,
    TextExport textExport,
    List<String> seedFeedUrls) {

  public FeedReaderProperties {
    if (scheduler == null) {
      scheduler = new Scheduler(true, "0 */15 * * * *");
    }
    if (job == null) {
      job = new Job(false);
    }
    if (http == null) {
      http = new Http("SReader/0.1", Duration.ofSeconds(5), Duration.ofSeconds(20), 1);
    }
    if (playwright == null) {
      playwright =
          new Playwright(
              false,
              true,
              1280,
              1600,
              Duration.ofSeconds(60),
              Duration.ofSeconds(5),
              null,
              null,
              20,
              3,
              Duration.ofMillis(2700));
    }
    if (textExport == null) {
      textExport = new TextExport(false, Path.of("/var/lib/sreader/content-text"), 100);
    }
    if (seedFeedUrls == null) {
      seedFeedUrls = List.of();
    }
  }

  public record Scheduler(boolean enabled, String cron) {}

  public record Job(boolean runOnce) {}

  public record Http(
      String userAgent, Duration connectTimeout, Duration readTimeout, int retryCount) {}

  public record TextExport(boolean enabled, Path outputDir, int batchSize) {

    public TextExport {
      if (outputDir == null) {
        outputDir = Path.of("/var/lib/sreader/content-text");
      }
      if (batchSize <= 0) {
        batchSize = 100;
      }
    }
  }

  public record Playwright(
      boolean enabled,
      boolean headless,
      int viewportWidth,
      int viewportHeight,
      Duration navigationTimeout,
      Duration networkIdleTimeout,
      Path infyExtensionDir,
      Path infyUserDataDir,
      int infyMaxScrolls,
      int infyStableRounds,
      Duration infyScrollWait) {

    public Playwright {
      if (viewportWidth <= 0) {
        viewportWidth = 1280;
      }
      if (viewportHeight <= 0) {
        viewportHeight = 1600;
      }
      if (navigationTimeout == null
          || navigationTimeout.isZero()
          || navigationTimeout.isNegative()) {
        navigationTimeout = Duration.ofSeconds(60);
      }
      if (networkIdleTimeout == null
          || networkIdleTimeout.isZero()
          || networkIdleTimeout.isNegative()) {
        networkIdleTimeout = Duration.ofSeconds(5);
      }
      if (infyMaxScrolls <= 0) {
        infyMaxScrolls = 20;
      }
      if (infyStableRounds <= 0) {
        infyStableRounds = 3;
      }
      if (infyScrollWait == null || infyScrollWait.isZero() || infyScrollWait.isNegative()) {
        infyScrollWait = Duration.ofMillis(2700);
      }
    }
  }
}
