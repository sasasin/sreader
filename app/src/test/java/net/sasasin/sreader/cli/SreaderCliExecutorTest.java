package net.sasasin.sreader.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import net.sasasin.sreader.service.canonicalization.ContentCanonicalizationMaintenanceService;
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

  @Test
  void contentCanonicalizeModeConflictIsUsageErrorWithoutCallingService() {
    ContentCanonicalizationMaintenanceService maintenance = mock();
    FullTextProbeService probe = mock();
    SreaderCliExecutor executor = executor(mock(FeedReaderScheduler.class), maintenance, probe);

    assertEquals(2, executor.execute("content", "canonicalize", "--dry-run", "--apply"));
    verifyNoInteractions(maintenance);
  }

  @Test
  void probeFeedSelectorConflictIsUsageErrorWithoutCallingService() {
    FullTextProbeService probe = mock();
    SreaderCliExecutor executor =
        executor(
            mock(FeedReaderScheduler.class),
            mock(ContentCanonicalizationMaintenanceService.class),
            probe);

    assertEquals(
        2,
        executor.execute(
            "probe",
            "feed",
            "--feed-url",
            "https://example.com/feed.xml",
            "--method",
            "http",
            "--entry",
            "first",
            "--entry-index",
            "0"));
    verifyNoInteractions(probe);
  }

  @Test
  void probeFeedInvalidSelectorConversionIsUsageErrorWithoutCallingService() {
    FullTextProbeService probe = mock();
    SreaderCliExecutor executor =
        executor(
            mock(FeedReaderScheduler.class),
            mock(ContentCanonicalizationMaintenanceService.class),
            probe);

    assertEquals(
        2,
        executor.execute(
            "probe",
            "feed",
            "--feed-url",
            "https://example.com/feed.xml",
            "--method",
            "http",
            "--entry",
            "newest"));
    verifyNoInteractions(probe);
  }

  @Test
  void probeArticleFeedMethodIsUsageErrorWithoutCallingService() {
    FullTextProbeService probe = mock();
    SreaderCliExecutor executor =
        executor(
            mock(FeedReaderScheduler.class),
            mock(ContentCanonicalizationMaintenanceService.class),
            probe);

    assertEquals(
        2,
        executor.execute("probe", "article", "--url", "https://example.com/a", "--method", "feed"));
    verifyNoInteractions(probe);
  }

  private SreaderCliExecutor executor(FeedReaderScheduler scheduler) {
    return executor(
        scheduler,
        mock(ContentCanonicalizationMaintenanceService.class),
        mock(FullTextProbeService.class));
  }

  private SreaderCliExecutor executor(
      FeedReaderScheduler scheduler,
      ContentCanonicalizationMaintenanceService maintenanceService,
      FullTextProbeService fullTextProbeService) {
    return new SreaderCliExecutor(
        new SreaderCommand(),
        new TestPicocliFactory(scheduler, maintenanceService, fullTextProbeService));
  }

  private static final class TestPicocliFactory extends PicocliFactory {
    private final FeedReaderScheduler scheduler;
    private final ContentCanonicalizationMaintenanceService maintenanceService;
    private final FullTextProbeService fullTextProbeService;
    private final FeedTomlService feedTomlService = mock(FeedTomlService.class);
    private final FeedDiscoveryService feedDiscoveryService = mock(FeedDiscoveryService.class);

    TestPicocliFactory(
        FeedReaderScheduler scheduler,
        ContentCanonicalizationMaintenanceService maintenanceService,
        FullTextProbeService fullTextProbeService) {
      super(null);
      this.scheduler = scheduler;
      this.maintenanceService = maintenanceService;
      this.fullTextProbeService = fullTextProbeService;
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
      if (cls == ContentCommand.class) {
        return cls.cast(new ContentCommand());
      }
      if (cls == ContentCanonicalizeCommand.class) {
        return cls.cast(new ContentCanonicalizeCommand(maintenanceService));
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
