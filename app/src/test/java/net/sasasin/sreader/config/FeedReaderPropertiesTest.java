package net.sasasin.sreader.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FeedReaderPropertiesTest {

  @Test
  void nullComponentsUseDocumentedDefaults() {
    FeedReaderProperties properties = new FeedReaderProperties(null, null, null, null, null, null);

    assertThat(properties.scheduler().cron()).isEqualTo("0 */15 * * * *");
    assertThat(properties.http().userAgent()).isEqualTo("SReader/0.1");
    assertThat(properties.http().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(properties.playwright().viewportWidth()).isEqualTo(1280);
    assertThat(properties.playwright().infyScrollWait()).isEqualTo(Duration.ofMillis(2700));
    assertThat(properties.textExport().outputDir())
        .isEqualTo(Path.of("/var/lib/sreader/content-text"));
  }

  @Test
  void nullNestedValuesUseDefaultsWhileValidValuesArePreserved() {
    FeedReaderProperties.Playwright playwright =
        new FeedReaderProperties.Playwright(
            null, null, null, null, null, null, null, null, null, null, null);

    assertThat(playwright.enabled()).isFalse();
    assertThat(playwright.headless()).isTrue();
    assertThat(playwright.viewportHeight()).isEqualTo(1600);
    assertThat(new FeedReaderProperties.Http("Test/1.0", null, null, 0).retryCount()).isZero();
  }

  @Test
  void rejectsExplicitInvalidNumbersDurationsAndBlankText() {
    assertThatThrownBy(() -> new FeedReaderProperties.TextExport(false, Path.of("out"), 0))
        .hasMessage("sreader.text-export.batch-size must be positive");
    assertThatThrownBy(
            () ->
                new FeedReaderProperties.Playwright(
                    true,
                    true,
                    0,
                    1,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    null,
                    null,
                    1,
                    1,
                    Duration.ofMillis(1)))
        .hasMessage("sreader.playwright.viewport-width must be positive");
    assertThatThrownBy(
            () -> new FeedReaderProperties.Http(" ", Duration.ZERO, Duration.ofSeconds(1), -1))
        .hasMessage("sreader.http.user-agent must not be blank");
    assertThatThrownBy(() -> new FeedReaderProperties.Scheduler(true, " "))
        .hasMessage("sreader.scheduler.cron must not be blank");
  }
}
