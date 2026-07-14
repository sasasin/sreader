package net.sasasin.sreader.cli;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "content",
    description = "Maintain stored content records.",
    mixinStandardHelpOptions = true,
    subcommands = ContentCanonicalizeCommand.class,
    usageHelpWidth = 100)
@Component
public class ContentCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().usage(spec.commandLine().getErr());
    return CommandLine.ExitCode.USAGE;
  }
}
