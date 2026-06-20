package net.sasasin.sreader.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Command(
    name = "feeds",
    description = "Import or export feed subscriptions as TOML.",
    mixinStandardHelpOptions = true,
    subcommands = {FeedImportCommand.class, FeedExportCommand.class},
    usageHelpWidth = 100)
@Component
public class FeedsCommand implements Runnable {

  @Override
  public void run() {
    // When "feeds" is given without subcommand, picocli automatically shows usage help.
  }
}
