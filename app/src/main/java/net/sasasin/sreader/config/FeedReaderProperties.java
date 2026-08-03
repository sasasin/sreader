package net.sasasin.sreader.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import net.sasasin.sreader.service.autopagerize.PaginationPolicy;
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
    Autopagerize autopagerize,
    List<String> seedFeedUrls) {

  public FeedReaderProperties {
    scheduler = scheduler == null ? Scheduler.defaults() : scheduler;
    job = job == null ? Job.defaults() : job;
    http = http == null ? Http.defaults() : http;
    playwright = playwright == null ? Playwright.defaults() : playwright;
    textExport = textExport == null ? TextExport.defaults() : textExport;
    autopagerize = autopagerize == null ? Autopagerize.defaults() : autopagerize;
    seedFeedUrls = seedFeedUrls == null ? List.of() : seedFeedUrls;
  }

  public record Scheduler(Boolean enabled, String cron) {
    static Scheduler defaults() {
      return new Scheduler(true, "0 */15 * * * *");
    }

    public Scheduler {
      enabled = enabled == null ? true : enabled;
      cron = requiredText(cron, "sreader.scheduler.cron", "0 */15 * * * *");
    }
  }

  public record Job(Boolean runOnce) {
    static Job defaults() {
      return new Job(false);
    }

    public Job {
      runOnce = runOnce == null ? false : runOnce;
    }
  }

  public record Http(
      String userAgent, Duration connectTimeout, Duration readTimeout, Integer retryCount) {
    static Http defaults() {
      return new Http("SReader/0.1", Duration.ofSeconds(5), Duration.ofSeconds(20), 1);
    }

    public Http {
      userAgent = requiredText(userAgent, "sreader.http.user-agent", "SReader/0.1");
      connectTimeout =
          positiveDuration(connectTimeout, "sreader.http.connect-timeout", Duration.ofSeconds(5));
      readTimeout =
          positiveDuration(readTimeout, "sreader.http.read-timeout", Duration.ofSeconds(20));
      retryCount = nonNegative(retryCount, "sreader.http.retry-count", 1);
    }
  }

  public record TextExport(Boolean enabled, Path outputDir, Integer batchSize) {
    static TextExport defaults() {
      return new TextExport(false, Path.of("/var/lib/sreader/content-text"), 100);
    }

    public TextExport {
      enabled = enabled == null ? false : enabled;
      outputDir = outputDir == null ? Path.of("/var/lib/sreader/content-text") : outputDir;
      batchSize = positive(batchSize, "sreader.text-export.batch-size", 100);
    }
  }

  public record Playwright(
      Boolean enabled,
      Boolean headless,
      Integer viewportWidth,
      Integer viewportHeight,
      Duration navigationTimeout,
      Duration networkIdleTimeout,
      Path infyExtensionDir,
      Path infyUserDataDir,
      Integer infyMaxScrolls,
      Integer infyStableRounds,
      Duration infyScrollWait) {
    static Playwright defaults() {
      return new Playwright(
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

    public Playwright {
      enabled = enabled == null ? false : enabled;
      headless = headless == null ? true : headless;
      viewportWidth = positive(viewportWidth, "sreader.playwright.viewport-width", 1280);
      viewportHeight = positive(viewportHeight, "sreader.playwright.viewport-height", 1600);
      navigationTimeout =
          positiveDuration(
              navigationTimeout, "sreader.playwright.navigation-timeout", Duration.ofSeconds(60));
      networkIdleTimeout =
          positiveDuration(
              networkIdleTimeout, "sreader.playwright.network-idle-timeout", Duration.ofSeconds(5));
      infyMaxScrolls = positive(infyMaxScrolls, "sreader.playwright.infy-max-scrolls", 20);
      infyStableRounds = positive(infyStableRounds, "sreader.playwright.infy-stable-rounds", 3);
      infyScrollWait =
          positiveDuration(
              infyScrollWait, "sreader.playwright.infy-scroll-wait", Duration.ofMillis(2700));
    }
  }

  /**
   * AutoPagerize pagination limits shared by HTTP and Playwright adapters. Defaults: 20 pages, 5
   * MiB per page, 20 MiB total, 120 s, same-origin only.
   */
  public record Autopagerize(
      Integer maxPages,
      Long maxPageBytes,
      Long maxTotalBytes,
      Duration totalTimeout,
      Boolean sameOriginOnly) {

    static Autopagerize defaults() {
      return new Autopagerize(
          20, 5L * 1024 * 1024, 20L * 1024 * 1024, Duration.ofSeconds(120), true);
    }

    public Autopagerize {
      maxPages = positive(maxPages, "sreader.autopagerize.max-pages", 20);
      maxPageBytes =
          positiveLong(maxPageBytes, "sreader.autopagerize.max-page-bytes", 5L * 1024 * 1024);
      maxTotalBytes =
          positiveLong(maxTotalBytes, "sreader.autopagerize.max-total-bytes", 20L * 1024 * 1024);
      totalTimeout =
          positiveDuration(
              totalTimeout, "sreader.autopagerize.total-timeout", Duration.ofSeconds(120));
      sameOriginOnly = sameOriginOnly == null ? true : sameOriginOnly;
      if (maxTotalBytes < maxPageBytes) {
        throw new IllegalArgumentException(
            "sreader.autopagerize.max-total-bytes must be >= max-page-bytes");
      }
    }

    public PaginationPolicy toPaginationPolicy() {
      return new PaginationPolicy(
          maxPages, maxPageBytes, maxTotalBytes, totalTimeout, sameOriginOnly);
    }
  }

  private static String requiredText(String value, String path, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException(path + " must not be blank");
    }
    return value;
  }

  private static Integer positive(Integer value, String path, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value <= 0) {
      throw new IllegalArgumentException(path + " must be positive");
    }
    return value;
  }

  private static Long positiveLong(Long value, String path, long defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value <= 0) {
      throw new IllegalArgumentException(path + " must be positive");
    }
    return value;
  }

  private static Integer nonNegative(Integer value, String path, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value < 0) {
      throw new IllegalArgumentException(path + " must be zero or positive");
    }
    return value;
  }

  private static Duration positiveDuration(Duration value, String path, Duration defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(path + " must be positive");
    }
    return value;
  }
}
