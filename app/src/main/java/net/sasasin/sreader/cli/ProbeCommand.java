package net.sasasin.sreader.cli;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "probe",
    description =
        "Probe article or feed URL with a chosen full text extraction method (no DB writes).",
    mixinStandardHelpOptions = true,
    subcommands = {ProbeArticleCommand.class, ProbeFeedCommand.class},
    usageHelpWidth = 100)
@Component
public class ProbeCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().usage(spec.commandLine().getErr());
    return CommandLine.ExitCode.USAGE;
  }
}
