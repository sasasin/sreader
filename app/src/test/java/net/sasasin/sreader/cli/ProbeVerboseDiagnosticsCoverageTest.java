package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionFallbackReason;
import net.sasasin.sreader.service.extraction.ExtractionSource;
import net.sasasin.sreader.service.extraction.PageTextContribution;
import net.sasasin.sreader.service.extraction.PaginationMetadata;
import net.sasasin.sreader.service.extraction.PaginationPageTrace;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import net.sasasin.sreader.service.probe.ProbeDocument;
import net.sasasin.sreader.service.probe.ProbeOutcome;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ProbeVerboseDiagnosticsCoverageTest {

  @Test
  void failureDiagnosticsPrintPaginationWithoutDocument() {
    PaginationMetadata meta =
        new PaginationMetadata(
            5L,
            "d".repeat(64),
            2,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1,
            PaginationStopReason.FETCH_FAILED,
            false,
            List.of(
                new PaginationPageTrace(
                    1,
                    URI.create("https://example.com/1"),
                    URI.create("https://example.com/1"),
                    9)),
            Optional.of(URI.create("https://example.com/2")),
            List.of(
                new PageTextContribution(
                    1,
                    ExtractionSource.PAGE_ELEMENT,
                    Optional.of(ExtractionFallbackReason.CONFIGURED_XPATH_NO_MATCH),
                    "x")));

    CommandLine cli = new CommandLine(new ProbeArticleCommand(mock(FullTextProbeService.class)));
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    cli.setErr(new PrintWriter(err));
    ProbeOutputWriter writer = new ProbeOutputWriter(cli.getCommandSpec());
    writer.writeFailureDiagnostics(null, Optional.of(meta), Optional.of("page failed"));
    cli.getErr().flush();
    String stderr = err.toString(StandardCharsets.UTF_8);
    assertThat(stderr)
        .contains("error:")
        .contains("page failed")
        .contains("AutoPagerize dataset:")
        .contains("Matched rule:")
        .contains("none")
        .contains("failed requested URL:")
        .contains("Per-page extraction source:");
  }

  @Test
  void succeededWithFallbackAndNoRulePrintsNone() {
    ProbeDocument document =
        new ProbeDocument(
            URI.create("https://in"),
            URI.create("https://out"),
            Optional.empty(),
            FullTextMethod.HTTP_AUTOPAGERIZE);
    PaginationMetadata meta =
        new PaginationMetadata(
            1L,
            "e".repeat(64),
            1,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            PaginationStopReason.NO_MATCHING_RULE,
            true,
            List.of(),
            Optional.empty(),
            List.of());
    ProbeOutcome.Succeeded succeeded =
        new ProbeOutcome.Succeeded(
            document,
            "body",
            ExtractionDecision.of(
                ExtractionSource.BODY_TEXT, ExtractionFallbackReason.CONFIGURED_XPATH_NO_MATCH),
            Optional.of(meta));

    CommandLine cli = new CommandLine(new ProbeArticleCommand(mock(FullTextProbeService.class)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    cli.setOut(new PrintWriter(out));
    cli.setErr(new PrintWriter(err));
    assertThat(
            new ProbeOutputWriter(cli.getCommandSpec()).writeSucceeded(succeeded, true, null, null))
        .isZero();
    cli.getOut().flush();
    cli.getErr().flush();
    assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("body");
    assertThat(err.toString(StandardCharsets.UTF_8))
        .contains("fallback:")
        .contains("CONFIGURED_XPATH_NO_MATCH")
        .contains("(none loaded)");
  }

  @Test
  void articleCommandVerboseFailureUsesPagination() {
    FullTextProbeService service = mock(FullTextProbeService.class);
    PaginationMetadata meta =
        new PaginationMetadata(
            1L,
            "f".repeat(64),
            1,
            true,
            Optional.of(0),
            Optional.of("R"),
            Optional.of("^x"),
            Optional.of("//a"),
            Optional.of("//div"),
            1,
            PaginationStopReason.FETCH_FAILED,
            false,
            List.of(
                new PaginationPageTrace(
                    1, URI.create("https://a/1"), URI.create("https://a/1"), 1)),
            Optional.of(URI.create("https://a/2")),
            List.of());
    when(service.probeArticle(any(), any(), any(), any()))
        .thenReturn(
            new ProbeOutcome.Failed(
                OperationFailure.of(
                    FailureStage.FETCH_ARTICLE_PAGE, FailureKind.IO, "https://a", "failed page"),
                Optional.of(meta)));

    CommandLine cli = new CommandLine(new ProbeArticleCommand(service));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    cli.setOut(new PrintWriter(out));
    cli.setErr(new PrintWriter(err));
    int code =
        cli.execute("--url", "https://example.com/a", "--method", "http_autopagerize", "--verbose");
    cli.getErr().flush();
    cli.getOut().flush();
    String stderr = err.toString(StandardCharsets.UTF_8);
    assertThat(code).isEqualTo(1);
    assertThat(stderr).contains("Error: failed page");
    assertThat(stderr).contains("AutoPagerize dataset:");
    assertThat(stderr).contains("explicit");
  }
}
