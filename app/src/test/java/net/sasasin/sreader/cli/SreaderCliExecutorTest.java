package net.sasasin.sreader.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import net.sasasin.sreader.service.feed.FeedDiscoveryService;
import net.sasasin.sreader.service.feed.toml.FeedTomlService;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import org.junit.jupiter.api.Test;

class SreaderCliExecutorTest {

  @Test
  void runOnceUsesSpringFactoryToInvokeScheduler() {
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);

    int exitCode = executor(scheduler).execute("run-once");

    assertEquals(0, exitCode);
    verify(scheduler).runIfIdle();
  }

  @Test
  void helpAndUnknownCommandUsePicocliExitCodes() {
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    SreaderCliExecutor executor = executor(scheduler);

    assertEquals(0, executor.execute("--help"));
    assertEquals(2, executor.execute("no-such-command"));
  }

  @Test
  void runOnceRuntimeExceptionMapsToOne() {
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    doThrow(new RuntimeException("boom")).when(scheduler).runIfIdle();

    assertEquals(1, executor(scheduler).execute("run-once"));
  }

  private SreaderCliExecutor executor(FeedReaderScheduler scheduler) {
    return new SreaderCliExecutor(new SreaderCommand(), new TestPicocliFactory(scheduler));
  }

  private static final class TestPicocliFactory extends PicocliFactory {
    private final FeedReaderScheduler scheduler;
    private final FeedTomlService feedTomlService = mock(FeedTomlService.class);
    private final FeedDiscoveryService feedDiscoveryService = mock(FeedDiscoveryService.class);
    private final FullTextProbeService fullTextProbeService = mock(FullTextProbeService.class);

    TestPicocliFactory(FeedReaderScheduler scheduler) {
      super(null);
      this.scheduler = scheduler;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
      if (cls == RunOnceCommand.class) {
        return cls.cast(new RunOnceCommand(scheduler));
      }
      if (cls == ProbeCommand.class) {
        return cls.cast(new ProbeCommand());
      }
      if (cls == ProbeArticleCommand.class) {
        return cls.cast(new ProbeArticleCommand(fullTextProbeService));
      }
      if (cls == ProbeFeedCommand.class) {
        return cls.cast(new ProbeFeedCommand(fullTextProbeService));
      }
      if (cls == FeedsDiscoverCommand.class) {
        return cls.cast(new FeedsDiscoverCommand(feedDiscoveryService));
      }
      if (cls == FeedImportCommand.class) {
        return cls.cast(new FeedImportCommand(feedTomlService));
      }
      if (cls == FeedExportCommand.class) {
        return cls.cast(new FeedExportCommand(feedTomlService));
      }
      return super.create(cls);
    }
  }
}
