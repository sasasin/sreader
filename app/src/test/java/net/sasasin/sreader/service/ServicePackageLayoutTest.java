package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the feature-package layout of {@code net.sasasin.sreader.service}: production types must
 * not reappear at the package root, expected feature directories must exist, and package
 * declarations must match source paths.
 */
class ServicePackageLayoutTest {

  private static final List<String> EXPECTED_FEATURE_DIRS =
      List.of(
          "article",
          "canonicalization",
          "extraction",
          "feed",
          "http",
          "job",
          "outcome",
          "probe",
          "text");

  private static final List<String> EXPECTED_FEED_SUBDIRS = List.of("ingestion", "toml");

  private static final List<String> EXPECTED_EXTRACTION_SUBDIRS = List.of("browser");

  @Test
  void productionTypesArePlacedInFeatureSubpackages() throws IOException {
    Path serviceRoot = serviceRoot();
    try (Stream<Path> files = Files.list(serviceRoot)) {
      List<Path> rootJavaFiles =
          files
              .filter(path -> path.getFileName().toString().endsWith(".java"))
              .filter(path -> !path.getFileName().toString().equals("package-info.java"))
              .toList();
      assertThat(rootJavaFiles).isEmpty();
    }
  }

  @Test
  void expectedFeatureDirectoriesExist() {
    Path serviceRoot = serviceRoot();
    for (String dir : EXPECTED_FEATURE_DIRS) {
      assertThat(serviceRoot.resolve(dir)).as(dir).isDirectory();
    }
    Path feed = serviceRoot.resolve("feed");
    for (String dir : EXPECTED_FEED_SUBDIRS) {
      assertThat(feed.resolve(dir)).as("feed/" + dir).isDirectory();
    }
    Path extraction = serviceRoot.resolve("extraction");
    for (String dir : EXPECTED_EXTRACTION_SUBDIRS) {
      assertThat(extraction.resolve(dir)).as("extraction/" + dir).isDirectory();
    }
  }

  @Test
  void packageDeclarationsMatchSourcePaths() throws IOException {
    Path serviceRoot = serviceRoot();
    try (Stream<Path> walk = Files.walk(serviceRoot)) {
      List<Path> javaFiles =
          walk.filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".java"))
              .toList();
      assertThat(javaFiles).isNotEmpty();
      for (Path file : javaFiles) {
        String relative = serviceRoot.relativize(file.getParent()).toString().replace('\\', '/');
        String expectedPackage =
            relative.isEmpty() || relative.equals(".")
                ? "net.sasasin.sreader.service"
                : "net.sasasin.sreader.service." + relative.replace('/', '.');
        String packageLine = firstPackageDeclaration(file);
        assertThat(packageLine).as(file.toString()).isEqualTo("package " + expectedPackage + ";");
      }
    }
  }

  private static String firstPackageDeclaration(Path file) throws IOException {
    try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
      return lines
          .map(String::trim)
          .filter(line -> line.startsWith("package "))
          .findFirst()
          .orElseThrow(() -> new AssertionError("missing package declaration: " + file));
    }
  }

  private static Path serviceRoot() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path fromAppModule = cwd.resolve("src/main/java/net/sasasin/sreader/service");
    if (Files.isDirectory(fromAppModule)) {
      return fromAppModule;
    }
    Path fromRepoRoot = cwd.resolve("app/src/main/java/net/sasasin/sreader/service");
    if (Files.isDirectory(fromRepoRoot)) {
      return fromRepoRoot;
    }
    throw new AssertionError("service source root not found from working directory: " + cwd);
  }
}
