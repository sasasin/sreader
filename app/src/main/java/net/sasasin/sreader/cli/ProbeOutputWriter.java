package net.sasasin.sreader.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionFallbackReason;
import net.sasasin.sreader.service.extraction.PaginationMetadata;
import net.sasasin.sreader.service.extraction.PaginationPageTrace;
import net.sasasin.sreader.service.probe.ProbeDocument;
import net.sasasin.sreader.service.probe.ProbeOutcome;
import picocli.CommandLine.Model.CommandSpec;

public class ProbeOutputWriter {

  private final CommandSpec spec;

  public ProbeOutputWriter(CommandSpec spec) {
    this.spec = spec;
  }

  public int writeSucceeded(
      ProbeOutcome.Succeeded succeeded, boolean verbose, String outputPath, Integer maxChars) {
    String text = succeeded.text();
    String outputText = text;
    if (maxChars != null && maxChars > 0 && outputText.length() > maxChars) {
      outputText = outputText.substring(0, maxChars);
    }

    if (verbose) {
      writeVerboseDiagnostics(
          succeeded.document(), succeeded.decision(), succeeded.pagination(), Optional.empty());
    }

    return writeBody(outputText, outputPath);
  }

  public void writeNoContentDiagnostics(
      ProbeDocument document, Optional<PaginationMetadata> pagination) {
    writeVerboseDiagnostics(document, null, pagination, Optional.empty());
  }

  public void writeFailureDiagnostics(
      ProbeDocument documentOrNull,
      Optional<PaginationMetadata> pagination,
      Optional<String> errorMessage) {
    if (documentOrNull != null) {
      writeVerboseDiagnostics(documentOrNull, null, pagination, errorMessage);
    } else {
      writePaginationOnly(pagination, errorMessage);
    }
  }

  private int writeBody(String outputText, String outputPath) {
    if (outputPath != null) {
      try {
        Files.writeString(Path.of(outputPath), outputText, StandardCharsets.UTF_8);
        spec.commandLine().getErr().println("Wrote probe output to " + outputPath);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write --output file: " + outputPath, e);
      }
      return CliExitCodes.SUCCESS;
    }
    spec.commandLine().getOut().print(outputText);
    return CliExitCodes.SUCCESS;
  }

  private void writeVerboseDiagnostics(
      ProbeDocument document,
      ExtractionDecision decision,
      Optional<PaginationMetadata> pagination,
      Optional<String> errorMessage) {
    PrintWriter err = spec.commandLine().getErr();
    err.printf("method:%n  %s%n", document.method().value());
    err.printf("input URL:%n  %s%n", document.inputUrl());
    err.printf("first final URL:%n  %s%n", document.finalUrl());
    if (decision != null) {
      err.printf("extractor source:%n  %s%n", decision.source().wireValue());
      err.printf(
          "fallback:%n  %s%n",
          decision.fallbackReason().map(ExtractionFallbackReason::name).orElse("none"));
    }
    document.title().ifPresent(title -> err.printf("title:%n  %s%n", title));
    errorMessage.ifPresent(msg -> err.printf("error:%n  %s%n", msg));
    pagination.ifPresent(this::writePaginationBlock);
  }

  private void writePaginationOnly(
      Optional<PaginationMetadata> pagination, Optional<String> errorMessage) {
    PrintWriter err = spec.commandLine().getErr();
    errorMessage.ifPresent(msg -> err.printf("error:%n  %s%n", msg));
    pagination.ifPresent(this::writePaginationBlock);
  }

  private void writePaginationBlock(PaginationMetadata meta) {
    PrintWriter err = spec.commandLine().getErr();
    err.println();
    err.println("AutoPagerize dataset:");
    err.printf("  id: %d%n", meta.datasetId());
    err.printf("  sha256: %s%n", meta.datasetSha256());
    err.printf("  importer version: %d%n", meta.importerVersion());
    err.printf(
        "  active or explicitly selected: %s%n",
        meta.explicitDatasetSelection() ? "explicit" : "active");

    err.println();
    err.println("Matched rule:");
    if (meta.ruleOrdinal().isEmpty()) {
      err.println("  none");
    } else {
      err.printf("  ordinal: %d%n", meta.ruleOrdinal().get());
      err.printf("  name: %s%n", meta.ruleName().orElse(""));
      err.printf("  url pattern: %s%n", meta.urlPattern().orElse(""));
      err.printf("  nextLink: %s%n", meta.nextLinkXpath().orElse(""));
      err.printf("  pageElement: %s%n", meta.pageElementXpath().orElse(""));
    }

    err.println();
    err.println("Pages:");
    if (meta.pages().isEmpty()) {
      err.println("  (none loaded)");
    } else {
      for (PaginationPageTrace page : meta.pages()) {
        err.printf(
            "  %d requested=%s final=%s bytes=%d%n",
            page.pageNumber(), page.requestedUri(), page.finalUri(), page.byteSize());
      }
    }
    meta.failedRequestedUrl().ifPresent(uri -> err.printf("  failed requested URL: %s%n", uri));
    meta.lastPageFinalUrl().ifPresent(uri -> err.printf("  last successful final URL: %s%n", uri));

    err.println();
    err.println("Pagination:");
    err.printf("  page count: %d%n", meta.pageCount());
    err.printf("  stop reason: %s%n", meta.stopReason().name());
    err.printf("  complete: %s%n", meta.complete());
    err.printf("  total bytes: %d%n", meta.totalBytes());

    if (!meta.contributions().isEmpty()) {
      err.println();
      err.println("Per-page extraction source:");
      meta.contributions()
          .forEach(
              c ->
                  err.printf(
                      "  %d source=%s fallback=%s%n",
                      c.pageNumber(),
                      c.source().wireValue(),
                      c.fallbackReason().map(Enum::name).orElse("none")));
    }
  }
}
