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
import java.util.stream.Stream;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.extraction.NoContentReason;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import net.sasasin.sreader.service.probe.ProbeDocument;
import net.sasasin.sreader.service.probe.ProbeOutcome;
import net.sasasin.sreader.service.probe.ProbeSkipReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;

class ProbeFeedCommandTest {

  @TempDir Path tempDir;

  @Test
  void passesDefaultFirstSelectionAndXpathToService() {
    FullTextProbeService service = serviceReturning(succeeded("body"));
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
  void resolvesEachIndividualSelector() {
    assertSelection(FeedEntrySelection.first(), "--entry", " FiRsT ");
    assertSelection(FeedEntrySelection.latest(), "--entry", " LaTeSt ");
    assertSelection(FeedEntrySelection.index(0), "--entry-index", "0");
    assertSelection(FeedEntrySelection.index(2), "--entry-index", "2");
    assertSelection(
        FeedEntrySelection.titleRegex("Release .*"), "--entry-title-regex", "Release .*");
    assertSelection(
        FeedEntrySelection.urlRegex("/posts/[0-9]+"), "--entry-url-regex", "/posts/[0-9]+");
  }

  @ParameterizedTest
  @MethodSource("conflictingEntrySelectors")
  void rejectsConflictingSelectorsWithoutCallingService(
      String optionA, String valueA, String optionB, String valueB) {
    FullTextProbeService service = mock(FullTextProbeService.class);
    Harness harness = harness(service);

    assertThat(harness.execute(optionA, valueA, optionB, valueB)).isEqualTo(2);
    assertThat(harness.stderr())
        .containsIgnoringCase("mutually exclusive")
        .contains(optionA)
        .contains(optionB);
    verifyNoInteractions(service);
  }

  static Stream<Arguments> conflictingEntrySelectors() {
    return Stream.of(
        Arguments.of("--entry", "first", "--entry-index", "0"),
        Arguments.of("--entry", "latest", "--entry-title-regex", "title"),
        Arguments.of("--entry", "first", "--entry-url-regex", "url"),
        Arguments.of("--entry-index", "1", "--entry-title-regex", "title"),
        Arguments.of("--entry-index", "1", "--entry-url-regex", "url"),
        Arguments.of("--entry-title-regex", "title", "--entry-url-regex", "url"));
  }

  @Test
  void rejectsThreeAndFourSelectorsWithoutCallingService() {
    FullTextProbeService service = mock(FullTextProbeService.class);

    Harness three = harness(service);
    assertThat(
            three.execute("--entry", "first", "--entry-index", "0", "--entry-title-regex", "title"))
        .isEqualTo(2);
    assertThat(three.stderr()).containsIgnoringCase("mutually exclusive");
    verifyNoInteractions(service);

    Harness four = harness(service);
    assertThat(
            four.execute(
                "--entry",
                "first",
                "--entry-index",
                "0",
                "--entry-title-regex",
                "title",
                "--entry-url-regex",
                "url"))
        .isEqualTo(2);
    assertThat(four.stderr()).containsIgnoringCase("mutually exclusive");
    verifyNoInteractions(service);
  }

  @Test
  void blankXpathIsIgnored() {
    FullTextProbeService service = serviceReturning(succeeded("body"));
    Harness harness = harness(service);

    assertThat(harness.execute("--xpath", " \t ")).isZero();
    verify(service)
        .probeFeed(
            URI.create("https://example.com/feed.xml"),
            FullTextMethod.HTTP,
            FeedEntrySelection.first(),
            Optional.empty());
  }

  @Test
  void blankSelectorsAreUsageErrorsWithoutCallingService() {
    FullTextProbeService service = mock(FullTextProbeService.class);

    Harness blankEntry = harness(service);
    assertThat(blankEntry.execute("--entry", "")).isEqualTo(2);
    assertThat(blankEntry.stderr()).contains("--entry");
    verifyNoInteractions(service);

    Harness blankIndex = harness(service);
    assertThat(blankIndex.execute("--entry-index", "")).isEqualTo(2);
    assertThat(blankIndex.stderr()).contains("--entry-index");
    verifyNoInteractions(service);

    Harness blankTitle = harness(service);
    assertThat(blankTitle.execute("--entry-title-regex", "  ")).isEqualTo(2);
    assertThat(blankTitle.stderr()).contains("--entry-title-regex");
    verifyNoInteractions(service);

    Harness blankUrl = harness(service);
    assertThat(blankUrl.execute("--entry-url-regex", "   ")).isEqualTo(2);
    assertThat(blankUrl.stderr()).contains("--entry-url-regex");
    verifyNoInteractions(service);
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
  void rejectsNegativeIndexOverflowAndInvalidRegexAsUsageErrorsWithoutCallingService() {
    FullTextProbeService service = mock(FullTextProbeService.class);

    Harness negativeIndex = harness(service);
    assertThat(negativeIndex.execute("--entry-index", "-1")).isEqualTo(2);
    assertThat(negativeIndex.stderr()).contains("--entry-index");
    verifyNoInteractions(service);

    Harness nonInteger = harness(service);
    assertThat(nonInteger.execute("--entry-index", "1.5")).isEqualTo(2);
    assertThat(nonInteger.stderr()).contains("--entry-index");
    verifyNoInteractions(service);

    Harness overflow = harness(service);
    assertThat(overflow.execute("--entry-index", "99999999999999999999")).isEqualTo(2);
    assertThat(overflow.stderr()).contains("--entry-index");
    verifyNoInteractions(service);

    Harness invalidTitle = harness(service);
    assertThat(invalidTitle.execute("--entry-title-regex", "[")).isEqualTo(2);
    assertThat(invalidTitle.stderr()).contains("--entry-title-regex");
    verifyNoInteractions(service);

    Harness invalidUrl = harness(service);
    assertThat(invalidUrl.execute("--entry-url-regex", "*")).isEqualTo(2);
    assertThat(invalidUrl.stderr()).contains("--entry-url-regex");
    verifyNoInteractions(service);
  }

  @Test
  void entrySelectionOptionsDefensiveInvariantRequiresExactlyOneMember() {
    ProbeFeedCommand.EntrySelectionOptions empty = new ProbeFeedCommand.EntrySelectionOptions();
    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, empty::selection);

    ProbeFeedCommand.EntrySelectionOptions multi = new ProbeFeedCommand.EntrySelectionOptions();
    multi.position = FeedEntrySelection.first();
    multi.index = FeedEntrySelection.index(0);
    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, multi::selection);
  }

  @Test
  void helpShowsEntrySelectionGroupWithoutPriorityWording() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CommandLine cli = new CommandLine(new ProbeFeedCommand(service));
    cli.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));

    assertThat(cli.execute("--help")).isZero();
    String help = out.toString(StandardCharsets.UTF_8);
    assertThat(help).contains("Entry selection");
    assertThat(help).contains("choose at most one");
    assertThat(help).contains("default: first");
    assertThat(help).contains("--entry");
    assertThat(help).contains("--entry-index");
    assertThat(help).contains("--entry-title-regex");
    assertThat(help).contains("--entry-url-regex");
    assertThat(help.toLowerCase()).doesNotContain("selection priority");
    assertThat(help.toLowerCase()).doesNotContain("priority:");
  }

  @Test
  void mapsServiceOutcomesToDocumentedExitCodes() {
    FullTextProbeService noMatch = mock(FullTextProbeService.class);
    when(noMatch.probeFeed(any(), any(), any(), any()))
        .thenReturn(new ProbeOutcome.NoMatchingEntry("details"));
    Harness noMatchHarness = harness(noMatch);
    assertThat(noMatchHarness.execute()).isEqualTo(3);
    assertThat(noMatchHarness.stderr()).contains("No matching feed entry: details");

    FullTextProbeService disabled = mock(FullTextProbeService.class);
    when(disabled.probeFeed(any(), any(), any(), any()))
        .thenReturn(new ProbeOutcome.Skipped(ProbeSkipReason.PLAYWRIGHT_DISABLED, "disabled"));
    Harness disabledHarness = harness(disabled);
    assertThat(disabledHarness.execute()).isEqualTo(5);
    assertThat(disabledHarness.stderr()).contains("disabled").doesNotContain("Error:");

    FullTextProbeService failing = mock(FullTextProbeService.class);
    when(failing.probeFeed(any(), any(), any(), any()))
        .thenReturn(
            new ProbeOutcome.Failed(
                OperationFailure.of(
                    FailureStage.FETCH_ARTICLE, FailureKind.IO, "https://x", "boom")));
    Harness failingHarness = harness(failing);
    assertThat(failingHarness.execute()).isEqualTo(1);
    assertThat(failingHarness.stderr()).contains("Error: boom");
  }

  @Test
  void returnsFourForNoContentAndWritesVerboseDiagnostics() {
    Harness noContent =
        harness(
            serviceReturning(
                new ProbeOutcome.NoContent(document(), NoContentReason.BODY_TEXT_EMPTY)));
    assertThat(noContent.execute()).isEqualTo(4);
    assertThat(noContent.stdout()).isEmpty();

    Harness verbose =
        harness(
            serviceReturning(
                new ProbeOutcome.NoContent(document(), NoContentReason.BODY_TEXT_EMPTY)));
    assertThat(verbose.execute("--verbose")).isEqualTo(4);
    assertThat(verbose.stdout()).isEmpty();
    assertThat(verbose.stderr())
        .contains("inputUrl=", "finalUrl=", "method=http", "title=Title", "textLength=0");
  }

  @Test
  void truncatesOutputWritesUtf8FileAndMapsWriteFailure() throws Exception {
    Harness truncated = harness(serviceReturning(succeeded("abcdefgh")));
    assertThat(truncated.execute("--max-chars", "4")).isZero();
    assertThat(truncated.stdout()).isEqualTo("abcd");

    Path output = tempDir.resolve("output.txt");
    Harness file = harness(serviceReturning(succeeded("こんにちは")));
    assertThat(file.execute("--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8)).isEqualTo("こんにちは");
    assertThat(file.stdout()).contains("Wrote probe output to " + output).doesNotContain("こんにちは");

    Harness failure = harness(serviceReturning(succeeded("body")));
    assertThat(failure.execute("--output", tempDir.toString())).isEqualTo(1);
    assertThat(failure.stderr()).contains("Failed to write --output file", tempDir.toString());
  }

  private void assertSelection(FeedEntrySelection expected, String... args) {
    FullTextProbeService service = serviceReturning(succeeded("body"));
    Harness harness = harness(service);
    assertThat(harness.execute(args)).isZero();
    verify(service)
        .probeFeed(
            URI.create("https://example.com/feed.xml"),
            FullTextMethod.HTTP,
            expected,
            Optional.empty());
  }

  private FullTextProbeService serviceReturning(ProbeOutcome outcome) {
    FullTextProbeService service = mock(FullTextProbeService.class);
    when(service.probeFeed(any(), any(), any(), any())).thenReturn(outcome);
    return service;
  }

  private ProbeOutcome.Succeeded succeeded(String text) {
    return new ProbeOutcome.Succeeded(document(), text);
  }

  private ProbeDocument document() {
    return new ProbeDocument(
        URI.create("https://example.com/feed.xml"),
        URI.create("https://example.com/article"),
        Optional.of("Title"),
        FullTextMethod.HTTP);
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
