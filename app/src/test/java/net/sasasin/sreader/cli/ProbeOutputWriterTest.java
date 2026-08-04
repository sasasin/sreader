package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionSource;
import net.sasasin.sreader.service.extraction.PaginationMetadata;
import net.sasasin.sreader.service.extraction.PaginationPageTrace;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import net.sasasin.sreader.service.probe.ProbeDocument;
import net.sasasin.sreader.service.probe.ProbeOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ProbeOutputWriterTest {

  @TempDir Path tempDir;

  @Test
  void writesTextWithoutExtraNewlineAndOnlyTruncatesPositiveSmallerLimits() {
    assertStdout("abcdef", null, "abcdef");
    assertStdout("abcdef", 0, "abcdef");
    assertStdout("abcdef", -1, "abcdef");
    assertStdout("abcdef", 6, "abcdef");
    assertStdout("abcdef", 10, "abcdef");
    assertStdout("abcdef", 3, "abc");
  }

  @Test
  void writesVerboseDiagnosticsWithMethodAndSource() {
    Harness titled = harness();
    assertThat(
            titled.writer.writeSucceeded(succeeded(Optional.of("Title"), "abcdef"), true, null, 3))
        .isZero();
    assertThat(titled.stderr())
        .contains("method:", "http")
        .contains("input URL:", "https://example.com/input")
        .contains("first final URL:", "https://example.com/final")
        .contains("extractor source:", "body_text")
        .contains("title:", "Title");
    assertThat(titled.stdout()).isEqualTo("abc");

    Harness untitled = harness();
    untitled.writer.writeSucceeded(succeeded(Optional.empty(), "x"), true, null, null);
    assertThat(untitled.stderr()).doesNotContain("title:").contains("extractor source:");
  }

  @Test
  void writesVerboseAutopagerizePageTrace() {
    PaginationMetadata meta =
        new PaginationMetadata(
            9L,
            "a".repeat(64),
            1,
            true,
            Optional.of(3),
            Optional.of("Example"),
            Optional.of("^https://example\\.com/"),
            Optional.of("//a[@rel='next']"),
            Optional.of("//div[@class='body']"),
            2,
            PaginationStopReason.NO_NEXT_LINK,
            true,
            List.of(
                new PaginationPageTrace(
                    1,
                    URI.create("https://example.com/1"),
                    URI.create("https://example.com/1"),
                    10),
                new PaginationPageTrace(
                    2,
                    URI.create("https://example.com/2"),
                    URI.create("https://example.com/2"),
                    20)),
            Optional.empty(),
            List.of());
    ProbeOutcome.Succeeded outcome =
        new ProbeOutcome.Succeeded(
            document(Optional.of("Title")),
            "page one\n\npage two",
            ExtractionDecision.of(ExtractionSource.PAGE_ELEMENT),
            Optional.of(meta));
    Harness harness = harness();
    harness.writer.writeSucceeded(outcome, true, null, null);
    assertThat(harness.stderr())
        .contains("AutoPagerize dataset:")
        .contains("id: 9")
        .contains("explicit")
        .contains("Matched rule:")
        .contains("ordinal: 3")
        .contains("Pages:")
        .contains("requested=https://example.com/1")
        .contains("Pagination:")
        .contains("stop reason: NO_NEXT_LINK")
        .contains("complete: true");
    assertThat(harness.stdout()).isEqualTo("page one\n\npage two");
  }

  @Test
  void writesNoContentDiagnostics() {
    Harness harness = harness();
    harness.writer.writeNoContentDiagnostics(document(Optional.of("Title")), Optional.empty());
    assertThat(harness.stderr()).contains("title:", "Title").contains("method:");
    assertThat(harness.stdout()).isEmpty();
  }

  @Test
  void writesUtf8FileAcknowledgesOutputAndWrapsIoException() throws Exception {
    Path output = tempDir.resolve("text.txt");
    Harness success = harness();
    assertThat(
            success.writer.writeSucceeded(
                succeeded(Optional.of("Title"), "こんにちは"), false, output.toString(), null))
        .isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8)).isEqualTo("こんにちは");
    assertThat(success.stdout())
        .isEqualTo("Wrote probe output to " + output + System.lineSeparator());

    Harness failure = harness();
    assertThatThrownBy(
            () ->
                failure.writer.writeSucceeded(
                    succeeded(Optional.of("Title"), "body"), false, tempDir.toString(), null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to write --output file: " + tempDir)
        .hasCauseInstanceOf(java.io.IOException.class);
  }

  private void assertStdout(String text, Integer maxChars, String expected) {
    Harness harness = harness();
    assertThat(
            harness.writer.writeSucceeded(
                succeeded(Optional.of("Title"), text), false, null, maxChars))
        .isZero();
    assertThat(harness.stdout()).isEqualTo(expected);
    assertThat(harness.stderr()).isEmpty();
  }

  private ProbeOutcome.Succeeded succeeded(Optional<String> title, String text) {
    return new ProbeOutcome.Succeeded(
        document(title), text, ExtractionDecision.of(ExtractionSource.BODY_TEXT), Optional.empty());
  }

  private ProbeDocument document(Optional<String> title) {
    return new ProbeDocument(
        URI.create("https://example.com/input"),
        URI.create("https://example.com/final"),
        title,
        FullTextMethod.HTTP);
  }

  private Harness harness() {
    CommandLine commandLine =
        new CommandLine(new ProbeFeedCommand(mock(FullTextProbeService.class)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    commandLine.setOut(new PrintWriter(out));
    commandLine.setErr(new PrintWriter(err));
    return new Harness(new ProbeOutputWriter(commandLine.getCommandSpec()), commandLine, out, err);
  }

  private record Harness(
      ProbeOutputWriter writer,
      CommandLine commandLine,
      ByteArrayOutputStream out,
      ByteArrayOutputStream err) {
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
