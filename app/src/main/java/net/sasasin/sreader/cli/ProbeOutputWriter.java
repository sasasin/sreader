package net.sasasin.sreader.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sasasin.sreader.domain.ProbeResult;
import picocli.CommandLine.Model.CommandSpec;

public class ProbeOutputWriter {

  private final CommandSpec spec;

  public ProbeOutputWriter(CommandSpec spec) {
    this.spec = spec;
  }

  public int writeResult(ProbeResult result, boolean verbose, String outputPath, Integer maxChars) {
    String text = result.text() != null ? result.text() : "";

    if (maxChars != null && maxChars > 0 && text.length() > maxChars) {
      text = text.substring(0, maxChars);
    }

    if (verbose) {
      PrintWriter err = spec.commandLine().getErr();
      err.printf("inputUrl=%s%n", result.inputUrl());
      err.printf("finalUrl=%s%n", result.finalUrl());
      err.printf("method=%s%n", result.method().value());
      if (result.title() != null) {
        err.printf("title=%s%n", result.title());
      }
      err.printf("textLength=%d%n", (result.text() != null ? result.text().length() : 0));
    }

    if (outputPath != null) {
      try {
        Files.writeString(Path.of(outputPath), text, StandardCharsets.UTF_8);
        spec.commandLine().getOut().println("Wrote probe output to " + outputPath);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write --output file: " + outputPath, e);
      }
      return 0;
    } else {
      // default: body to STDOUT, no extra
      spec.commandLine().getOut().print(text);
      return 0;
    }
  }

  public int writeEmptyOrError(int codeForEmpty) {
    // for cases where we decide before result
    return codeForEmpty;
  }
}
