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
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.ProbeResult;
import net.sasasin.sreader.service.FullTextProbeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;

class ProbeArticleCommandTest {

  @TempDir Path tempDir;

  @Test
  void httpSuccessWritesBodyToStdoutAndReturnsZero() {
    FullTextProbeService service = serviceReturning("body text here");
    Harness harness = harness(service);

    assertThat(harness.execute()).isZero();
    assertThat(harness.stdout()).isEqualTo("body text here");
    assertThat(harness.stderr()).isEmpty();

    ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
    ArgumentCaptor<Optional<String>> xpath = ArgumentCaptor.forClass(Optional.class);
    verify(service).probeArticle(uri.capture(), eq(FullTextMethod.HTTP), xpath.capture());
    assertThat(uri.getValue()).isEqualTo(URI.create("https://example.com/article"));
    assertThat(xpath.getValue()).isEmpty();
  }

  @Test
  void nonblankXpathIsPassedToService() {
    FullTextProbeService service = serviceReturning("hit");
    Harness harness = harness(service);

    assertThat(harness.execute("--xpath", "//article")).isZero();
    verify(service)
        .probeArticle(
            URI.create("https://example.com/article"),
            FullTextMethod.HTTP,
            Optional.of("//article"));
    assertThat(harness.stdout()).isEqualTo("hit");
  }

  @Test
  void blankXpathIsTreatedAsEmptyOptional() {
    FullTextProbeService service = serviceReturning("body");
    Harness harness = harness(service);

    assertThat(harness.execute("--xpath", " \t ")).isZero();
    verify(service)
        .probeArticle(
            URI.create("https://example.com/article"), FullTextMethod.HTTP, Optional.empty());
  }

  @Test
  void maxCharsTruncatesStdout() {
    FullTextProbeService service = serviceReturning("abcdefgh");
    Harness harness = harness(service);

    assertThat(harness.execute("--max-chars", "4")).isZero();
    assertThat(harness.stdout()).isEqualTo("abcd");
  }

  @Test
  void outputFileWritesUtf8AndAcknowledges() throws Exception {
    Path output = tempDir.resolve("article.txt");
    FullTextProbeService service = serviceReturning("こんにちは");
    Harness harness = harness(service);

    assertThat(harness.execute("--output", output.toString())).isZero();
    assertThat(Files.readString(output, StandardCharsets.UTF_8)).isEqualTo("こんにちは");
    assertThat(harness.stdout())
        .contains("Wrote probe output to " + output)
        .doesNotContain("こんにちは");
    assertThat(harness.stderr()).isEmpty();
  }

  @Test
  void rejectsFeedMethodAsUsageErrorWithoutCallingService() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    Harness harness = harness(service);

    assertThat(harness.commandLine.execute("--url", "https://example.com/a", "--method", "feed"))
        .isEqualTo(2);
    assertThat(harness.stderr()).contains("--method feed");
    verifyNoInteractions(service);
  }

  @Test
  void rejectsInvalidUrlAsUsageErrorWithoutCallingService() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    Harness harness = harness(service);

    assertThat(harness.commandLine.execute("--url", "not-a-url", "--method", "http")).isEqualTo(2);
    assertThat(harness.stderr()).containsIgnoringCase("http");
    verifyNoInteractions(service);
  }

  @Test
  void missingRequiredOptionsAreUsageErrors() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    Harness noUrl = harness(service);
    assertThat(noUrl.commandLine.execute("--method", "http")).isEqualTo(2);
    verifyNoInteractions(service);

    Harness noMethod = harness(service);
    assertThat(noMethod.commandLine.execute("--url", "https://example.com/a")).isEqualTo(2);
    verifyNoInteractions(service);
  }

  @Test
  void returnsFourForNullTextWithoutWritingBody() {
    Harness harness = harness(serviceReturning(null));
    assertThat(harness.execute()).isEqualTo(4);
    assertThat(harness.stdout()).isEmpty();
    assertThat(harness.stderr()).isEmpty();
  }

  @Test
  void returnsFourForBlankTextWithoutVerboseDiagnostics() {
    Harness harness = harness(serviceReturning(" \n\t"));
    assertThat(harness.execute()).isEqualTo(4);
    assertThat(harness.stdout()).isEmpty();
    assertThat(harness.stderr()).isEmpty();
  }

  @Test
  void blankTextWithVerboseStillReturnsFourAfterDiagnostics() {
    Harness harness = harness(serviceReturning(" \n\t"));
    assertThat(harness.execute("--verbose")).isEqualTo(4);
    assertThat(harness.stdout()).isEmpty();
    assertThat(harness.stderr())
        .contains("inputUrl=", "finalUrl=", "method=http", "title=Title", "textLength=3");
  }

  @Test
  void nonblankResultUsesWriterReturnCodeZero() {
    Harness harness = harness(serviceReturning("ok"));
    assertThat(harness.execute()).isZero();
    assertThat(harness.stdout()).isEqualTo("ok");
  }

  @Test
  void playwrightDisabledMapsToExitFiveWithoutErrorPrefix() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    when(service.probeArticle(any(), any(), any()))
        .thenThrow(new FullTextProbeService.PlaywrightDisabledException("playwright off"));
    Harness harness = harness(service);

    assertThat(harness.execute()).isEqualTo(5);
    assertThat(harness.stderr()).contains("playwright off").doesNotContain("Error:");
  }

  @Test
  void genericRuntimeExceptionMapsToExitOneWithErrorPrefix() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    when(service.probeArticle(any(), any(), any())).thenThrow(new RuntimeException("boom"));
    Harness harness = harness(service);

    assertThat(harness.execute()).isEqualTo(1);
    assertThat(harness.stderr()).contains("Error: boom");
  }

  @Test
  void outputFileFailureMapsToExitOneWithoutSuccessAcknowledgement() {
    FullTextProbeService service = serviceReturning("body");
    Harness harness = harness(service);

    assertThat(harness.execute("--output", tempDir.toString())).isEqualTo(1);
    assertThat(harness.stderr()).contains("Failed to write --output file", tempDir.toString());
    assertThat(harness.stdout()).doesNotContain("Wrote probe output");
  }

  private FullTextProbeService serviceReturning(String text) {
    FullTextProbeService service = mock(FullTextProbeService.class);
    when(service.probeArticle(any(), any(), any())).thenReturn(result(text));
    return service;
  }

  private ProbeResult result(String text) {
    return new ProbeResult(
        URI.create("https://example.com/article"),
        URI.create("https://example.com/final"),
        "Title",
        FullTextMethod.HTTP,
        text);
  }

  private Harness harness(FullTextProbeService service) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    CommandLine commandLine = new CommandLine(new ProbeArticleCommand(service));
    commandLine.setOut(new PrintWriter(out));
    commandLine.setErr(new PrintWriter(err));
    return new Harness(commandLine, out, err);
  }

  private record Harness(
      CommandLine commandLine, ByteArrayOutputStream out, ByteArrayOutputStream err) {
    int execute(String... extraArgs) {
      String[] required = {"--url", "https://example.com/article", "--method", "http"};
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
