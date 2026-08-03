package net.sasasin.sreader.cli;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "autopagerize",
    description = "Import and manage AutoPagerize SITEINFO datasets (local JSON only).",
    mixinStandardHelpOptions = true,
    subcommands = {
      AutopagerizeImportCommand.class,
      AutopagerizeDatasetsCommand.class,
    },
    usageHelpWidth = 100)
@Component
public class AutopagerizeCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().usage(spec.commandLine().getErr());
    return CommandLine.ExitCode.USAGE;
  }
}
