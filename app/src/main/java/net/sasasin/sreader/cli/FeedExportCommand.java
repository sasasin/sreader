package net.sasasin.sreader.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sasasin.sreader.service.feed.toml.FeedTomlService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "export",
    description = "Export current feeds (optionally filtered) as TOML to stdout or a file.",
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class FeedExportCommand implements Runnable {

  private final FeedTomlService feedTomlService;

  @Option(
      names = {"--output", "-o"},
      paramLabel = "<PATH>",
      description = "Write TOML to this file instead of printing to stdout")
  private String output;

  @Option(
      names = "--active-only",
      description = "Export only feeds that have status=active (exclude unsubscribed)")
  private boolean activeOnly;

  public FeedExportCommand(FeedTomlService feedTomlService) {
    this.feedTomlService = feedTomlService;
  }

  @Override
  public void run() {
    String toml = feedTomlService.exportToml(activeOnly);
    try {
      if (output == null) {
        System.out.print(toml);
      } else {
        Files.writeString(Path.of(output), toml, StandardCharsets.UTF_8);
        System.out.println("Exported feeds to " + output);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to write output file: " + output, e);
    }
  }
}
