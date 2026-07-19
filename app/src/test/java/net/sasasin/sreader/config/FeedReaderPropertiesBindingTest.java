package net.sasasin.sreader.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sasasin.sreader.SreaderApplication;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class FeedReaderPropertiesBindingTest {

  @Test
  void bindingConvertsKebabCaseDurationsPathsAndLists() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("sreader.scheduler.enabled", "false");
    values.put("sreader.scheduler.cron", "0 0 * * * *");
    values.put("sreader.job.run-once", "true");
    values.put("sreader.http.user-agent", "BindingTest/1.0");
    values.put("sreader.http.connect-timeout", "1500ms");
    values.put("sreader.http.read-timeout", "45s");
    values.put("sreader.http.retry-count", "3");
    values.put("sreader.playwright.enabled", "true");
    values.put("sreader.playwright.headless", "false");
    values.put("sreader.playwright.viewport-width", "1024");
    values.put("sreader.playwright.viewport-height", "768");
    values.put("sreader.playwright.navigation-timeout", "30s");
    values.put("sreader.playwright.network-idle-timeout", "2500ms");
    values.put("sreader.playwright.infy-extension-dir", "/tmp/extension");
    values.put("sreader.playwright.infy-user-data-dir", "/tmp/profile");
    values.put("sreader.playwright.infy-max-scrolls", "7");
    values.put("sreader.playwright.infy-stable-rounds", "2");
    values.put("sreader.playwright.infy-scroll-wait", "900ms");
    values.put("sreader.text-export.enabled", "true");
    values.put("sreader.text-export.output-dir", "/tmp/export");
    values.put("sreader.text-export.batch-size", "25");
    values.put("sreader.seed-feed-urls[0]", "https://example.com/a.xml");
    values.put("sreader.seed-feed-urls[1]", "https://example.com/b.xml");

    FeedReaderProperties properties = bind(values);

    assertEquals(false, properties.scheduler().enabled());
    assertEquals("0 0 * * * *", properties.scheduler().cron());
    assertEquals(true, properties.job().runOnce());
    assertEquals("BindingTest/1.0", properties.http().userAgent());
    assertEquals(Duration.ofMillis(1500), properties.http().connectTimeout());
    assertEquals(Duration.ofSeconds(45), properties.http().readTimeout());
    assertEquals(3, properties.http().retryCount());
    assertEquals(true, properties.playwright().enabled());
    assertEquals(false, properties.playwright().headless());
    assertEquals(1024, properties.playwright().viewportWidth());
    assertEquals(768, properties.playwright().viewportHeight());
    assertEquals(Duration.ofSeconds(30), properties.playwright().navigationTimeout());
    assertEquals(Duration.ofMillis(2500), properties.playwright().networkIdleTimeout());
    assertEquals(Path.of("/tmp/extension"), properties.playwright().infyExtensionDir());
    assertEquals(Path.of("/tmp/profile"), properties.playwright().infyUserDataDir());
    assertEquals(7, properties.playwright().infyMaxScrolls());
    assertEquals(2, properties.playwright().infyStableRounds());
    assertEquals(Duration.ofMillis(900), properties.playwright().infyScrollWait());
    assertEquals(true, properties.textExport().enabled());
    assertEquals(Path.of("/tmp/export"), properties.textExport().outputDir());
    assertEquals(25, properties.textExport().batchSize());
    assertEquals(
        List.of("https://example.com/a.xml", "https://example.com/b.xml"),
        properties.seedFeedUrls());
  }

  @Test
  void bindingRejectsExplicitInvalidBoundaryValuesWithPropertyPaths() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("sreader.playwright.viewport-width", "0");
    values.put("sreader.playwright.viewport-height", "-1");
    values.put("sreader.playwright.navigation-timeout", "0s");
    values.put("sreader.playwright.network-idle-timeout", "-1ms");
    values.put("sreader.playwright.infy-max-scrolls", "0");
    values.put("sreader.playwright.infy-stable-rounds", "-1");
    values.put("sreader.playwright.infy-scroll-wait", "0ms");
    values.put("sreader.text-export.batch-size", "0");

    BindException exception = assertThrows(BindException.class, () -> bind(values));

    Assertions.assertThat(exception)
        .hasStackTraceContaining("sreader.playwright.viewport-width must be positive");
  }

  @Test
  void bindingRejectsInvalidIntegerSyntax() {
    BindException exception =
        assertThrows(
            BindException.class,
            () -> bind(Map.of("sreader.playwright.viewport-width", "not-an-integer")));

    assertEquals(true, exception.getMessage().contains("sreader.playwright.viewport-width"));
  }

  @Test
  void bindingConvertsEmptyOptionalInfyPathToNull() {
    FeedReaderProperties properties =
        bind(
            Map.of(
                "sreader.playwright.enabled",
                "false",
                "sreader.playwright.infy-extension-dir",
                "",
                "sreader.playwright.infy-user-data-dir",
                ""));

    assertNull(properties.playwright().infyExtensionDir());
    assertNull(properties.playwright().infyUserDataDir());
  }

  @Test
  void bindingPreservesDefaultsWhenConfigurationGroupsAreAbsent() {
    FeedReaderProperties properties = bind(Map.of("sreader.job.run-once", "true"));

    assertEquals(true, properties.scheduler().enabled());
    assertEquals("0 */15 * * * *", properties.scheduler().cron());
    assertEquals(true, properties.job().runOnce());
    assertEquals("SReader/0.1", properties.http().userAgent());
    assertEquals(Duration.ofSeconds(5), properties.http().connectTimeout());
    assertEquals(1280, properties.playwright().viewportWidth());
    assertEquals(Path.of("/var/lib/sreader/content-text"), properties.textExport().outputDir());
  }

  @Test
  void applicationContextFailsFastForInvalidConfiguredValue() {
    SpringApplication application = new SpringApplication(SreaderApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);

    Assertions.assertThatThrownBy(
            () ->
                application.run(
                    "--sreader.scheduler.enabled=false", "--sreader.playwright.viewport-width=0"))
        .hasStackTraceContaining("sreader.playwright.viewport-width must be positive");
  }

  private static FeedReaderProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bind("sreader", Bindable.of(FeedReaderProperties.class))
        .orElseThrow(() -> new AssertionError("sreader properties should bind"));
  }
}
