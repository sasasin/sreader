package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.ProbeResult;
import net.sasasin.sreader.service.FullTextProbeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;

class ProbeFeedCommandTest {

  @TempDir Path tempDir;

  @Test
  void passesDefaultFirstSelectionAndXpathToService() {
    FullTextProbeService service = serviceReturning("body");
    Harness harness = harness(service);

    assertThat(harness.execute("--xpath", "//article")).isZero();
    ArgumentCaptor<FeedEntrySelection> selection =
        ArgumentCaptor.forClass(FeedEntrySelection.class);
    ArgumentCaptor<Optional<String>> xpath = ArgumentCaptor.forClass(Optional.class);
    verify(service)
        .probeFeed(
            eq(URI.create("https://example.com/feed.xml")),
            eq(FullTextMethod.HTTP),
            selection.capture(),
            xpath.capture());
    assertThat(selection.getValue()).isEqualTo(FeedEntrySelection.first());
    assertThat(xpath.getValue()).contains("//article");
    assertThat(harness.stdout()).isEqualTo("body");
    assertThat(harness.stderr()).isEmpty();
  }

  @Test
  void resolvesEachSelectorAndItsPriority() {
    assertSelection(FeedEntrySelection.first(), "--entry", " FiRsT ");
    assertSelection(FeedEntrySelection.latest(), "--entry", " LaTeSt ");
    assertSelection(FeedEntrySelection.index(2), "--entry-index", "2");
    assertSelection(
        FeedEntrySelection.titleRegex("Release .*"), "--entry-title-regex", "Release .*");
    assertSelection(
        FeedEntrySelection.urlRegex("/posts/[0-9]+"), "--entry-url-regex", "/posts/[0-9]+");
    assertSelection(
        FeedEntrySelection.index(3),
        "--entry-index",
        "3",
        "--entry-title-regex",
        "title",
        "--entry-url-regex",
        "url",
        "--entry",
        "latest");
    assertSelection(
        FeedEntrySelection.titleRegex("title"),
        "--entry-title-regex",
        "title",
        "--entry-url-regex",
        "url",
        "--entry",
        "latest");
    assertSelection(
        FeedEntrySelection.urlRegex("url"), "--entry-url-regex", "url", "--entry", "latest");
  }

  @Test
  void blankRegexesFallThroughAndBlankXpathIsIgnored() {
    FullTextProbeService service = serviceReturning("body");
    Harness harness = harness(service);

    assertThat(
            harness.execute(
                "--entry-title-regex", "  ", "--entry-url-regex", "url", "--xpath", " \t "))
        .isZero();
    verify(service)
        .probeFeed(
            URI.create("https://example.com/feed.xml"),
            FullTextMethod.HTTP,
            FeedEntrySelection.urlRegex("url"),
            Optional.empty());
  }

  @Test
  void rejectsInvalidInputBeforeCallingService() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    Harness invalidUrl = harness(service);
    assertThat(invalidUrl.commandLine.execute("--feed-url", "not-a-url", "--method", "http"))
        .isEqualTo(2);
    assertThat(invalidUrl.stderr()).contains("http or https");
    verifyNoInteractions(service);

    Harness invalidEntry = harness(service);
    assertThat(invalidEntry.execute("--entry", "newest")).isEqualTo(2);
    assertThat(invalidEntry.stderr()).contains("first or latest");
    verifyNoInteractions(service);

    Harness xpathWithFeed = harness(service);
    assertThat(
            xpathWithFeed.commandLine.execute(
                "--feed-url", "https://example.com/feed.xml", "--method", "feed", "--xpath", "//p"))
        .isEqualTo(2);
    assertThat(xpathWithFeed.stderr()).contains("cannot be used");
    verifyNoInteractions(service);
  }

  @Test
  void mapsServiceOutcomesToDocumentedExitCodes() {
    FullTextProbeService noMatch = mock(FullTextProbeService.class);
    when(noMatch.probeFeed(any(), any(), any(), any()))
        .thenThrow(new FullTextProbeService.NoMatchingEntryException("details"));
    Harness noMatchHarness = harness(noMatch);
    assertThat(noMatchHarness.execute()).isEqualTo(3);
    assertThat(noMatchHarness.stderr()).contains("No matching feed entry: details");

    FullTextProbeService disabled = mock(FullTextProbeService.class);
    when(disabled.probeFeed(any(), any(), any(), any()))
        .thenThrow(new FullTextProbeService.PlaywrightDisabledException("disabled"));
    Harness disabledHarness = harness(disabled);
    assertThat(disabledHarness.execute()).isEqualTo(5);
    assertThat(disabledHarness.stderr()).contains("disabled").doesNotContain("Error:");

    FullTextProbeService failing = mock(FullTextProbeService.class);
    when(failing.probeFeed(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
    Harness failingHarness = harness(failing);
    assertThat(failingHarness.execute()).isEqualTo(1);
    assertThat(failingHarness.stderr()).contains("Error: boom");
  }

  @Test
  void returnsFourForNullOrBlankResultsAndWritesVerboseDiagnostics() {
    Harness nullResult = harness(serviceReturning(null));
    assertThat(nullResult.execute()).isEqualTo(4);
    assertThat(nullResult.stdout()).isEmpty();

    Harness blankResult = harness(serviceReturning(" \n\t"));
    assertThat(blankResult.execute("--verbose")).isEqualTo(4);
    assertThat(blankResult.stdout()).isEmpty();
    assertThat(blankResult.stderr())
        .contains("inputUrl=", "finalUrl=", "method=http", "title=Title", "textLength=3");
  }

  @Test
  void truncatesOutputWritesUtf8FileAndMapsWriteFailure() throws Exception {
    Harness truncated = harness(serviceReturning("abcdefgh"));
    assertThat(truncated.execute("--max-chars", "4")).isZero();
    assertThat(truncated.stdout()).isEqualTo("abcd");

    Path output = tempDir.resolve("output.txt");
    Harness file = harness(serviceReturning("こんにちは"));
    assertThat(file.execute("--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8)).isEqualTo("こんにちは");
    assertThat(file.stdout()).contains("Wrote probe output to " + output).doesNotContain("こんにちは");

    Harness failure = harness(serviceReturning("body"));
    assertThat(failure.execute("--output", tempDir.toString())).isEqualTo(1);
    assertThat(failure.stderr()).contains("Failed to write --output file", tempDir.toString());
  }

  private void assertSelection(FeedEntrySelection expected, String... args) {
    FullTextProbeService service = serviceReturning("body");
    Harness harness = harness(service);
    assertThat(harness.execute(args)).isZero();
    verify(service)
        .probeFeed(
            URI.create("https://example.com/feed.xml"),
            FullTextMethod.HTTP,
            expected,
            Optional.empty());
  }

  private FullTextProbeService serviceReturning(String text) {
    FullTextProbeService service = mock(FullTextProbeService.class);
    when(service.probeFeed(any(), any(), any(), any())).thenReturn(result(text));
    return service;
  }

  private ProbeResult result(String text) {
    return new ProbeResult(
        URI.create("https://example.com/feed.xml"),
        URI.create("https://example.com/article"),
        "Title",
        FullTextMethod.HTTP,
        text);
  }

  private Harness harness(FullTextProbeService service) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    CommandLine commandLine = new CommandLine(new ProbeFeedCommand(service));
    commandLine.setOut(new PrintWriter(out));
    commandLine.setErr(new PrintWriter(err));
    return new Harness(commandLine, out, err);
  }

  private record Harness(
      CommandLine commandLine, ByteArrayOutputStream out, ByteArrayOutputStream err) {
    int execute(String... extraArgs) {
      String[] required = {"--feed-url", "https://example.com/feed.xml", "--method", "http"};
      String[] args =
          java.util.stream.Stream.concat(
                  java.util.Arrays.stream(required), java.util.Arrays.stream(extraArgs))
              .toArray(String[]::new);
      return commandLine.execute(args);
    }

    String stdout() {
      commandLine.getOut().flush();
      return out.toString(StandardCharsets.UTF_8);
    }

    String stderr() {
      commandLine.getErr().flush();
      return err.toString(StandardCharsets.UTF_8);
    }
  }
}
