package net.sasasin.sreader.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import net.sasasin.sreader.config.FeedReaderProperties.Playwright;
import net.sasasin.sreader.config.FeedReaderProperties.TextExport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class FeedReaderPropertiesTest {

  @Test
  void topLevelNullComponentsUseDocumentedDefaults() {
    FeedReaderProperties properties = new FeedReaderProperties(null, null, null, null, null, null);

    assertEquals(
        new FeedReaderProperties.Scheduler(true, "0 */15 * * * *"), properties.scheduler());
    assertEquals(new FeedReaderProperties.Job(false), properties.job());
    assertEquals(
        new FeedReaderProperties.Http(
            "SReader/0.1", Duration.ofSeconds(5), Duration.ofSeconds(20), 1),
        properties.http());
    assertEquals(defaultPlaywright(), properties.playwright());
    assertEquals(
        new TextExport(false, Path.of("/var/lib/sreader/content-text"), 100),
        properties.textExport());
    assertEquals(List.of(), properties.seedFeedUrls());
  }

  @Test
  void topLevelNonNullComponentsArePreserved() {
    FeedReaderProperties.Scheduler scheduler =
        new FeedReaderProperties.Scheduler(false, "0 0 * * * *");
    FeedReaderProperties.Job job = new FeedReaderProperties.Job(true);
    FeedReaderProperties.Http http =
        new FeedReaderProperties.Http("Test/1.0", Duration.ofMillis(1), Duration.ofDays(1), 3);
    Playwright playwright =
        new Playwright(
            true,
            false,
            1,
            2,
            Duration.ofNanos(1),
            Duration.ofMillis(1),
            Path.of("extension"),
            Path.of("profile"),
            1,
            2,
            Duration.ofNanos(1));
    TextExport textExport = new TextExport(true, Path.of("relative/export"), 1);
    List<String> seedUrls = List.of("https://example.com/a.xml", "https://example.com/b.xml");

    FeedReaderProperties properties =
        new FeedReaderProperties(scheduler, job, http, playwright, textExport, seedUrls);

    assertEquals(scheduler, properties.scheduler());
    assertEquals(job, properties.job());
    assertEquals(http, properties.http());
    assertEquals(playwright, properties.playwright());
    assertEquals(textExport, properties.textExport());
    assertEquals(seedUrls, properties.seedFeedUrls());
  }

  @Test
  void nullPlaywrightUsesDefaultWithoutReplacingCustomHttp() {
    FeedReaderProperties.Http http =
        new FeedReaderProperties.Http(
            "Custom/1.0", Duration.ofSeconds(1), Duration.ofSeconds(2), 0);

    FeedReaderProperties properties = new FeedReaderProperties(null, null, http, null, null, null);

    assertEquals(http, properties.http());
    assertEquals(defaultPlaywright(), properties.playwright());
  }

  @Test
  void textExportDefaultsNullOutputDirectory() {
    assertEquals(
        Path.of("/var/lib/sreader/content-text"), new TextExport(false, null, 1).outputDir());
  }

  @Test
  void textExportPreservesNonNullPaths() {
    assertEquals(
        Path.of("/tmp/export"), new TextExport(true, Path.of("/tmp/export"), 1).outputDir());
    assertEquals(
        Path.of("relative/export"),
        new TextExport(true, Path.of("relative/export"), 1).outputDir());
    assertEquals(Path.of(""), new TextExport(true, Path.of(""), 1).outputDir());
  }

  @ParameterizedTest(name = "batch size {0} becomes {1}")
  @CsvSource({
    "-1, 100",
    "-2147483648, 100",
    "0, 100",
    "1, 1",
    "100, 100",
    "2147483647, 2147483647"
  })
  void textExportDefaultsNonPositiveBatchSize(int input, int expected) {
    assertEquals(expected, new TextExport(true, Path.of("export"), input).batchSize());
  }

  @ParameterizedTest(name = "viewport width {0} becomes {1}")
  @CsvSource({
    "-1, 1280",
    "-2147483648, 1280",
    "0, 1280",
    "1, 1",
    "1280, 1280",
    "2147483647, 2147483647"
  })
  void playwrightDefaultsNonPositiveViewportWidth(int input, int expected) {
    assertEquals(
        expected,
        playwright(
                input, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1, Duration.ofMillis(1))
            .viewportWidth());
  }

  @ParameterizedTest(name = "viewport height {0} becomes {1}")
  @CsvSource({
    "-1, 1600",
    "-2147483648, 1600",
    "0, 1600",
    "1, 1",
    "1600, 1600",
    "2147483647, 2147483647"
  })
  void playwrightDefaultsNonPositiveViewportHeight(int input, int expected) {
    assertEquals(
        expected,
        playwright(
                1, input, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1, Duration.ofMillis(1))
            .viewportHeight());
  }

  @ParameterizedTest(name = "navigation timeout {0} becomes {1}")
  @MethodSource("navigationTimeouts")
  void playwrightDefaultsInvalidNavigationTimeout(Duration input, Duration expected) {
    assertEquals(
        expected,
        playwright(1, 1, input, Duration.ofSeconds(1), 1, 1, Duration.ofMillis(1))
            .navigationTimeout());
  }

  static Stream<Arguments> navigationTimeouts() {
    return Stream.of(
        Arguments.of(null, Duration.ofSeconds(60)),
        Arguments.of(Duration.ZERO, Duration.ofSeconds(60)),
        Arguments.of(Duration.ofNanos(-1), Duration.ofSeconds(60)),
        Arguments.of(Duration.ofSeconds(-1), Duration.ofSeconds(60)),
        Arguments.of(Duration.ofNanos(1), Duration.ofNanos(1)),
        Arguments.of(Duration.ofSeconds(60), Duration.ofSeconds(60)),
        Arguments.of(Duration.ofDays(10_000), Duration.ofDays(10_000)));
  }

  @ParameterizedTest(name = "network idle timeout {0} becomes {1}")
  @MethodSource("networkIdleTimeouts")
  void playwrightDefaultsInvalidNetworkIdleTimeout(Duration input, Duration expected) {
    assertEquals(
        expected,
        playwright(1, 1, Duration.ofSeconds(1), input, 1, 1, Duration.ofMillis(1))
            .networkIdleTimeout());
  }

  static Stream<Arguments> networkIdleTimeouts() {
    return Stream.of(
        Arguments.of(null, Duration.ofSeconds(5)),
        Arguments.of(Duration.ZERO, Duration.ofSeconds(5)),
        Arguments.of(Duration.ofMillis(-1), Duration.ofSeconds(5)),
        Arguments.of(Duration.ofNanos(1), Duration.ofNanos(1)),
        Arguments.of(Duration.ofMillis(2500), Duration.ofMillis(2500)));
  }

  @ParameterizedTest(name = "max scrolls {0} becomes {1}")
  @CsvSource({"-1, 20", "-2147483648, 20", "0, 20", "1, 1", "20, 20", "2147483647, 2147483647"})
  void playwrightDefaultsNonPositiveMaxScrolls(int input, int expected) {
    assertEquals(
        expected,
        playwright(
                1, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), input, 1, Duration.ofMillis(1))
            .infyMaxScrolls());
  }

  @ParameterizedTest(name = "stable rounds {0} becomes {1}")
  @CsvSource({"-1, 3", "-2147483648, 3", "0, 3", "1, 1", "3, 3", "2147483647, 2147483647"})
  void playwrightDefaultsNonPositiveStableRounds(int input, int expected) {
    assertEquals(
        expected,
        playwright(
                1, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, input, Duration.ofMillis(1))
            .infyStableRounds());
  }

  @ParameterizedTest(name = "scroll wait {0} becomes {1}")
  @MethodSource("scrollWaits")
  void playwrightDefaultsInvalidScrollWait(Duration input, Duration expected) {
    assertEquals(
        expected,
        playwright(1, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1, input)
            .infyScrollWait());
  }

  static Stream<Arguments> scrollWaits() {
    return Stream.of(
        Arguments.of(null, Duration.ofMillis(2700)),
        Arguments.of(Duration.ZERO, Duration.ofMillis(2700)),
        Arguments.of(Duration.ofMillis(-1), Duration.ofMillis(2700)),
        Arguments.of(Duration.ofNanos(1), Duration.ofNanos(1)),
        Arguments.of(Duration.ofMillis(2700), Duration.ofMillis(2700)),
        Arguments.of(Duration.ofDays(10_000), Duration.ofDays(10_000)));
  }

  @Test
  void playwrightDefaultsMultipleInvalidValuesIndependently() {
    Playwright properties = playwright(0, -1, null, Duration.ZERO, 0, -10, Duration.ofMillis(-1));

    assertEquals(1280, properties.viewportWidth());
    assertEquals(1600, properties.viewportHeight());
    assertEquals(Duration.ofSeconds(60), properties.navigationTimeout());
    assertEquals(Duration.ofSeconds(5), properties.networkIdleTimeout());
    assertEquals(20, properties.infyMaxScrolls());
    assertEquals(3, properties.infyStableRounds());
    assertEquals(Duration.ofMillis(2700), properties.infyScrollWait());
  }

  private static Playwright playwright(
      int width,
      int height,
      Duration navigationTimeout,
      Duration networkIdleTimeout,
      int maxScrolls,
      int stableRounds,
      Duration scrollWait) {
    return new Playwright(
        true,
        false,
        width,
        height,
        navigationTimeout,
        networkIdleTimeout,
        Path.of("/tmp/extension"),
        Path.of("/tmp/profile"),
        maxScrolls,
        stableRounds,
        scrollWait);
  }

  private static Playwright defaultPlaywright() {
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
}
