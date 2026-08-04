package net.sasasin.sreader.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FeedReaderPropertiesTest {

  @Test
  void nullComponentsUseDocumentedDefaults() {
    FeedReaderProperties properties =
        new FeedReaderProperties(null, null, null, null, null, null, null);

    assertThat(properties.scheduler().cron()).isEqualTo("0 */15 * * * *");
    assertThat(properties.http().userAgent()).isEqualTo("SReader/0.1");
    assertThat(properties.http().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(properties.playwright().viewportWidth()).isEqualTo(1280);
    assertThat(properties.playwright().networkIdleTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(properties.textExport().outputDir())
        .isEqualTo(Path.of("/var/lib/sreader/content-text"));
    assertThat(properties.autopagerize().maxPages()).isEqualTo(20);
    assertThat(properties.autopagerize().maxPageBytes()).isEqualTo(5L * 1024 * 1024);
    assertThat(properties.autopagerize().maxTotalBytes()).isEqualTo(20L * 1024 * 1024);
    assertThat(properties.autopagerize().totalTimeout()).isEqualTo(Duration.ofSeconds(120));
    assertThat(properties.autopagerize().sameOriginOnly()).isTrue();
  }

  @Test
  void nullNestedValuesUseDefaultsWhileValidValuesArePreserved() {
    FeedReaderProperties.Playwright playwright =
        new FeedReaderProperties.Playwright(null, null, null, null, null, null);

    assertThat(playwright.enabled()).isFalse();
    assertThat(playwright.headless()).isTrue();
    assertThat(playwright.viewportHeight()).isEqualTo(1600);
    assertThat(new FeedReaderProperties.Http("Test/1.0", null, null, 0).retryCount()).isZero();
    assertThat(new FeedReaderProperties.Job(null).runOnce()).isFalse();
    FeedReaderProperties.TextExport textExport =
        new FeedReaderProperties.TextExport(null, null, null);
    assertThat(textExport.enabled()).isFalse();
    assertThat(textExport.outputDir()).isEqualTo(Path.of("/var/lib/sreader/content-text"));
    assertThat(textExport.batchSize()).isEqualTo(100);
  }

  @Test
  void rejectsExplicitInvalidNumbersDurationsAndBlankText() {
    assertThatThrownBy(() -> new FeedReaderProperties.TextExport(false, Path.of("out"), 0))
        .hasMessage("sreader.text-export.batch-size must be positive");
    assertThatThrownBy(
            () ->
                new FeedReaderProperties.Playwright(
                    true, true, 0, 1, Duration.ofSeconds(1), Duration.ofSeconds(1)))
        .hasMessage("sreader.playwright.viewport-width must be positive");
    assertThatThrownBy(
            () -> new FeedReaderProperties.Http(" ", Duration.ZERO, Duration.ofSeconds(1), -1))
        .hasMessage("sreader.http.user-agent must not be blank");
    assertThatThrownBy(() -> new FeedReaderProperties.Scheduler(true, " "))
        .hasMessage("sreader.scheduler.cron must not be blank");
    assertThatThrownBy(
            () ->
                new FeedReaderProperties.Autopagerize(0, 1024L, 2048L, Duration.ofSeconds(1), true))
        .hasMessage("sreader.autopagerize.max-pages must be positive");
    assertThatThrownBy(
            () ->
                new FeedReaderProperties.Autopagerize(2, 4096L, 1024L, Duration.ofSeconds(1), true))
        .hasMessage("sreader.autopagerize.max-total-bytes must be >= max-page-bytes");
    assertThatThrownBy(
            () -> new FeedReaderProperties.Autopagerize(1, 0L, 1024L, Duration.ofSeconds(1), true))
        .hasMessage("sreader.autopagerize.max-page-bytes must be positive");
    assertThatThrownBy(
            () -> new FeedReaderProperties.Autopagerize(1, 1024L, 0L, Duration.ofSeconds(1), true))
        .hasMessage("sreader.autopagerize.max-total-bytes must be positive");
    assertThatThrownBy(
            () -> new FeedReaderProperties.Autopagerize(1, 1024L, 2048L, Duration.ZERO, true))
        .hasMessage("sreader.autopagerize.total-timeout must be positive");
  }

  @Test
  void autopagerizeNullsUseDefaults() {
    FeedReaderProperties.Autopagerize cfg =
        new FeedReaderProperties.Autopagerize(null, null, null, null, null);
    assertThat(cfg.maxPages()).isEqualTo(20);
    assertThat(cfg.maxPageBytes()).isEqualTo(5L * 1024 * 1024);
    assertThat(cfg.maxTotalBytes()).isEqualTo(20L * 1024 * 1024);
    assertThat(cfg.totalTimeout()).isEqualTo(Duration.ofSeconds(120));
    assertThat(cfg.sameOriginOnly()).isTrue();
    assertThat(cfg.toPaginationPolicy().maxPages()).isEqualTo(20);
  }
}
