package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards targeted facades against reintroducing manual construction of Spring-managed
 * collaborators.
 */
class ManagedCollaboratorConstructionTest {

  private static final List<Guard> GUARDS =
      List.of(
          new Guard(
              "feed/toml/FeedTomlService.java",
              List.of(
                  "new FeedTomlReader(",
                  "new FeedTomlWriter(",
                  "new FeedImportPlanner(",
                  "new FeedImportExecutor(",
                  "Clock.systemDefaultZone()")),
          new Guard(
              "canonicalization/ContentCanonicalizationMaintenanceService.java",
              List.of(
                  "new ContentCanonicalizationCandidateScanner(",
                  "new ContentCanonicalizationPlanner(",
                  "new ContentCanonicalizationExecutor(",
                  "new ContentCanonicalizationFileCleaner(")),
          new Guard(
              "feed/ingestion/FeedEntryImportService.java",
              List.of("new FeedDocumentService(", "new FeedEntryImporter(")),
          new Guard("probe/FullTextProbeService.java", List.of("new ProbeDocumentFetcher(")),
          new Guard(
              "extraction/browser/PlaywrightHtmlSource.java",
              List.of(
                  "new PlaywrightRuntime(",
                  "new PlaywrightPageNavigator(",
                  "new InfyScrollDriver(",
                  "new InfyScrollPageRenderer(",
                  "new StandardPlaywrightPageRenderer(",
                  "new PlaywrightResourceLifecycle(",
                  "buildCollaborators(")),
          new Guard(
              "http/HttpFetchService.java", List.of("HttpClient.newBuilder(", "createClient(")),
          new Guard("extraction/HtmlTextExtractor.java", List.of("new Readability4JExtended(")));

  @Test
  void targetedFacadesDoNotManuallyConstructManagedCollaborators() throws IOException {
    Path serviceRoot = serviceRoot();
    for (Guard guard : GUARDS) {
      Path file = serviceRoot.resolve(guard.relativePath());
      assertThat(file).as(guard.relativePath()).exists();
      String source = Files.readString(file, StandardCharsets.UTF_8);
      for (String forbidden : guard.forbiddenSnippets()) {
        assertThat(source)
            .as("%s must not contain %s", guard.relativePath(), forbidden)
            .doesNotContain(forbidden);
      }
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

  private record Guard(String relativePath, List<String> forbiddenSnippets) {}
}
