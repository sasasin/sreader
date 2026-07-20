package net.sasasin.sreader.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sasasin.sreader.service.probe.ProbeDocument;
import picocli.CommandLine.Model.CommandSpec;

public class ProbeOutputWriter {

  private final CommandSpec spec;

  public ProbeOutputWriter(CommandSpec spec) {
    this.spec = spec;
  }

  public int writeSucceeded(
      ProbeDocument document, String text, boolean verbose, String outputPath, Integer maxChars) {
    String outputText = text;
    if (maxChars != null && maxChars > 0 && outputText.length() > maxChars) {
      outputText = outputText.substring(0, maxChars);
    }

    if (verbose) {
      writeDiagnostics(document, text.length());
    }

    if (outputPath != null) {
      try {
        Files.writeString(Path.of(outputPath), outputText, StandardCharsets.UTF_8);
        spec.commandLine().getOut().println("Wrote probe output to " + outputPath);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write --output file: " + outputPath, e);
      }
      return CliExitCodes.SUCCESS;
    }
    spec.commandLine().getOut().print(outputText);
    return CliExitCodes.SUCCESS;
  }

  public void writeNoContentDiagnostics(ProbeDocument document) {
    writeDiagnostics(document, 0);
  }

  private void writeDiagnostics(ProbeDocument document, int textLength) {
    PrintWriter err = spec.commandLine().getErr();
    err.printf("inputUrl=%s%n", document.inputUrl());
    err.printf("finalUrl=%s%n", document.finalUrl());
    err.printf("method=%s%n", document.method().value());
    document.title().ifPresent(title -> err.printf("title=%s%n", title));
    err.printf("textLength=%d%n", textLength);
  }
}
