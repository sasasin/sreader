package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;

/**
 * Guards that full-text method definitions stay centralized in {@link FullTextMethod} and are not
 * re-scattered across services, CLI, or browser adapters.
 */
class FullTextMethodCatalogArchitectureTest {

  private static final List<String> TARGETED_FEED_EQUALITY_FILES =
      List.of(
          "service/extraction/FullTextExtractionService.java",
          "service/probe/FullTextProbeService.java",
          "service/feed/ingestion/FeedEntryImporter.java",
          "cli/ProbeArticleCommand.java",
          "cli/ProbeFeedCommand.java");

  /**
   * Unique multi-segment wire values that should not appear as hardcoded string literals outside
   * the catalog. Short values like {@code feed}/{@code http} appear in CLI user messages and are
   * checked separately via the pipe-list and capability guards.
   */
  private static final List<String> UNIQUE_WIRE_LITERALS =
      FullTextMethod.wireValues().stream().filter(value -> value.contains("_")).toList();

  @Test
  void extractionPlanAndPlaywrightRenderModeTypesAreGone() {
    Path mainJava = mainJavaRoot();
    assertThat(mainJava.resolve("domain/ExtractionPlan.java")).doesNotExist();
    assertThat(mainJava.resolve("service/extraction/browser/PlaywrightRenderMode.java"))
        .doesNotExist();
  }

  @Test
  void productionSourcesDoNotReferenceRemovedPlanTypes() throws IOException {
    List<String> hits = new ArrayList<>();
    for (Path file : productionJavaFiles()) {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      for (String forbidden :
          List.of("ExtractionPlan", "useInfyScroll", "PlaywrightRenderMode", "ExtractorKind")) {
        if (source.contains(forbidden)) {
          hits.add(relativeToMain(file) + " contains " + forbidden);
        }
      }
      // SourceKind was part of ExtractionPlan; only flag the nested-type style usage.
      if (source.contains("ExtractionPlan.SourceKind") || source.contains("SourceKind.FEED")) {
        hits.add(relativeToMain(file) + " contains ExtractionPlan SourceKind usage");
      }
    }
    assertThat(hits).isEmpty();
  }

  @Test
  void uniqueWireValueLiteralsAppearOnlyInFullTextMethod() throws IOException {
    Path catalog = mainJavaRoot().resolve("domain/FullTextMethod.java");
    assertThat(catalog).isRegularFile();
    assertThat(UNIQUE_WIRE_LITERALS)
        .contains(
            "http_readability",
            "playwright_readability",
            "playwright_infy_scroll",
            "playwright_infy_scroll_readability");
    for (Path file : productionJavaFiles()) {
      if (file.equals(catalog)) {
        continue;
      }
      String source = Files.readString(file, StandardCharsets.UTF_8);
      for (String wire : UNIQUE_WIRE_LITERALS) {
        assertThat(source)
            .as("wire value %s must not be hardcoded in %s", wire, relativeToMain(file))
            .doesNotContain("\"" + wire + "\"");
      }
    }
  }

  @Test
  void extractionAndProbeServicesDispatchOnDefinitionNotSevenConstants() throws IOException {
    for (String relative :
        List.of(
            "service/extraction/FullTextExtractionService.java",
            "service/probe/ProbeDocumentFetcher.java")) {
      String source = Files.readString(mainJavaRoot().resolve(relative), StandardCharsets.UTF_8);
      assertThat(source).contains("method.definition()");
      assertThat(source).doesNotContain("case FEED ->");
      assertThat(source).doesNotContain("case HTTP ->");
      assertThat(source).doesNotContain("case PLAYWRIGHT ->");
      assertThat(source).doesNotContain("case HTTP_READABILITY ->");
    }
  }

  @Test
  void converterUsesCatalogSupportedValues() throws IOException {
    String source =
        Files.readString(
            mainJavaRoot().resolve("cli/FullTextMethodConverter.java"), StandardCharsets.UTF_8);
    assertThat(source).contains("FullTextMethod.supportedValues()");
    assertThat(source).doesNotContain("Arrays.stream(FullTextMethod.values())");
  }

  @Test
  void probeCommandsDoNotHardcodeSevenWireValuesInDescription() throws IOException {
    for (String relative : List.of("cli/ProbeArticleCommand.java", "cli/ProbeFeedCommand.java")) {
      String source = Files.readString(mainJavaRoot().resolve(relative), StandardCharsets.UTF_8);
      assertThat(source)
          .doesNotContain(
              "feed|http|http_readability|playwright|playwright_readability"
                  + "|playwright_infy_scroll|playwright_infy_scroll_readability");
    }
  }

  @Test
  void targetedCallersDoNotCompareAgainstFeedConstant() throws IOException {
    for (String relative : TARGETED_FEED_EQUALITY_FILES) {
      String source = Files.readString(mainJavaRoot().resolve(relative), StandardCharsets.UTF_8);
      assertThat(source)
          .as(relative)
          .doesNotContain("== FullTextMethod.FEED")
          .doesNotContain("!= FullTextMethod.FEED");
    }
  }

  @Test
  void fullTextMethodCatalogDoesNotUseEnumOrdinal() throws IOException {
    String source =
        Files.readString(
            mainJavaRoot().resolve("domain/FullTextMethod.java"), StandardCharsets.UTF_8);
    assertThat(source).doesNotContain(".ordinal()");
  }

  private static List<Path> productionJavaFiles() throws IOException {
    try (Stream<Path> walk = Files.walk(mainJavaRoot())) {
      return walk.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .toList();
    }
  }

  private static String relativeToMain(Path file) {
    return mainJavaRoot().relativize(file).toString().replace('\\', '/');
  }

  private static Path mainJavaRoot() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path fromAppModule = cwd.resolve("src/main/java/net/sasasin/sreader");
    if (Files.isDirectory(fromAppModule)) {
      return fromAppModule;
    }
    Path fromRepoRoot = cwd.resolve("app/src/main/java/net/sasasin/sreader");
    if (Files.isDirectory(fromRepoRoot)) {
      return fromRepoRoot;
    }
    throw new AssertionError("main java root not found from working directory: " + cwd);
  }
}
