package net.sasasin.sreader.cli;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "feeds",
    description = "Import or export feed subscriptions as TOML.",
    mixinStandardHelpOptions = true,
    subcommands = {
      FeedImportCommand.class,
      FeedExportCommand.class,
      FeedsDiscoverCommand.class,
    },
    usageHelpWidth = 100)
@Component
public class FeedsCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().usage(spec.commandLine().getErr());
    return CommandLine.ExitCode.USAGE;
  }
}
