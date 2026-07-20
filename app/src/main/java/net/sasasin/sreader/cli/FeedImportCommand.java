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
    name = "import",
    description = "Import feeds from a TOML file. Creates/updates feed_url records.",
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class FeedImportCommand implements Runnable {

  private final FeedTomlService feedTomlService;

  @Option(
      names = {"--input", "-i"},
      paramLabel = "<PATH>",
      description = "Path to the TOML file containing feeds to import (required)",
      required = true)
  private String input;

  @Option(
      names = "--dry-run",
      description = "Parse and validate the TOML but do not modify the database")
  private boolean dryRun;

  @Option(
      names = "--resubscribe",
      description =
          "When a feed is unsubscribed in DB but listed as active in TOML, resubscribe it")
  private boolean resubscribe;

  public FeedImportCommand(FeedTomlService feedTomlService) {
    this.feedTomlService = feedTomlService;
  }

  @Override
  public void run() {
    try {
      String toml = Files.readString(Path.of(input), StandardCharsets.UTF_8);
      FeedTomlService.ImportResult result =
          feedTomlService.importToml(toml, new FeedTomlService.ImportOptions(dryRun, resubscribe));
      System.out.println(
          "Import result: inserted="
              + result.inserted()
              + ", updated="
              + result.updated()
              + ", unchanged="
              + result.unchanged()
              + ", unsubscribed="
              + result.unsubscribed()
              + ", resubscribed="
              + result.resubscribed()
              + ", conflicts="
              + result.conflicts()
              + ", errors="
              + result.errors().size());
      for (String conflict : result.conflictMessages()) {
        System.out.println("Conflict: " + conflict);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read input file: " + input, e);
    }
  }
}
