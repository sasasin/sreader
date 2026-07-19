package net.sasasin.sreader.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

/** Executes SReader CLI commands with Spring-backed dependency injection. */
@Component
public class SreaderCliExecutor {

  private final SreaderCommand sreaderCommand;
  private final PicocliFactory picocliFactory;

  public SreaderCliExecutor(SreaderCommand sreaderCommand, PicocliFactory picocliFactory) {
    this.sreaderCommand = sreaderCommand;
    this.picocliFactory = picocliFactory;
  }

  public int execute(String... args) {
    CommandLine cli = new CommandLine(sreaderCommand, picocliFactory);
    cli.setExitCodeExceptionMapper(
        exception ->
            exception instanceof CommandLine.ParameterException
                ? CliExitCodes.USAGE_ERROR
                : CliExitCodes.EXECUTION_ERROR);
    return cli.execute(args);
  }
}
