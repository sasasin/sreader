package net.sasasin.sreader.cli;

import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Command(
    name = "run-once",
    description = "Run the feed reader job once, then exit.",
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class RunOnceCommand implements Runnable {

  private final FeedReaderScheduler scheduler;

  public RunOnceCommand(FeedReaderScheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public void run() {
    scheduler.runIfIdle();
  }
}
