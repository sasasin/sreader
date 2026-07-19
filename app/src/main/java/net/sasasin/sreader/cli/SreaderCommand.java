package net.sasasin.sreader.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Command(
    name = "sreader",
    description = {
      "SReader: lightweight RSS/Atom feed reader.",
      "When invoked without a subcommand (and without --help), the application starts ",
      "normally. Scheduler runs according to sreader.scheduler.* properties unless disabled. ",
      "Use the run-once subcommand to run jobs once then exit.",
    },
    mixinStandardHelpOptions = true,
    subcommands = {
      FeedsCommand.class,
      ContentCommand.class,
      ProbeCommand.class,
      RunOnceCommand.class,
    },
    usageHelpWidth = 100)
@Component
public class SreaderCommand implements Runnable {

  @Override
  public void run() {
    // No-op for bare root invocation. Actual default daemon / run-once handling is done
    // in FeedReaderCommandRunner so that the Spring lifecycle (scheduling) is preserved.
  }
}
