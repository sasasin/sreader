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
import java.util.Optional;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import net.sasasin.sreader.service.probe.ProbeDocument;
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
  void writesVerboseDiagnosticsUsingOriginalTextLengthAndOptionalTitle() {
    Harness titled = harness();
    assertThat(
            titled.writer.writeSucceeded(document(Optional.of("Title")), "abcdef", true, null, 3))
        .isZero();
    assertThat(titled.stderr())
        .contains("inputUrl=https://example.com/input", "finalUrl=https://example.com/final")
        .contains("method=http", "title=Title", "textLength=6");
    assertThat(titled.stdout()).isEqualTo("abc");

    Harness untitled = harness();
    untitled.writer.writeSucceeded(document(Optional.empty()), "x", true, null, null);
    assertThat(untitled.stderr()).doesNotContain("title=").contains("textLength=1");
  }

  @Test
  void writesNoContentDiagnosticsWithZeroLength() {
    Harness harness = harness();
    harness.writer.writeNoContentDiagnostics(document(Optional.of("Title")));
    assertThat(harness.stderr()).contains("textLength=0", "title=Title");
    assertThat(harness.stdout()).isEmpty();
  }

  @Test
  void writesUtf8FileAcknowledgesOutputAndWrapsIoException() throws Exception {
    Path output = tempDir.resolve("text.txt");
    Harness success = harness();
    assertThat(
            success.writer.writeSucceeded(
                document(Optional.of("Title")), "こんにちは", false, output.toString(), null))
        .isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8)).isEqualTo("こんにちは");
    assertThat(success.stdout())
        .isEqualTo("Wrote probe output to " + output + System.lineSeparator());

    Harness failure = harness();
    assertThatThrownBy(
            () ->
                failure.writer.writeSucceeded(
                    document(Optional.of("Title")), "body", false, tempDir.toString(), null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to write --output file: " + tempDir)
        .hasCauseInstanceOf(java.io.IOException.class);
  }

  private void assertStdout(String text, Integer maxChars, String expected) {
    Harness harness = harness();
    assertThat(
            harness.writer.writeSucceeded(
                document(Optional.of("Title")), text, false, null, maxChars))
        .isZero();
    assertThat(harness.stdout()).isEqualTo(expected);
    assertThat(harness.stderr()).isEmpty();
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
