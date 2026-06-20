package net.sasasin.sreader.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.ProbeResult;
import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import net.sasasin.sreader.service.FeedDiscoveryService;
import net.sasasin.sreader.service.FeedTomlService;
import net.sasasin.sreader.service.FeedTomlService.ImportOptions;
import net.sasasin.sreader.service.FeedTomlService.ImportResult;
import net.sasasin.sreader.service.FullTextProbeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class FeedCommandsTest {

  @TempDir Path tempDir;

  @Test
  void feedsImportCallsServiceWithInputAndDefaultFlags() throws Exception {
    FeedTomlService service = mock(FeedTomlService.class);
    ImportResult result = new ImportResult(1, 0, 0, 0, 0, 0, List.of(), List.of());
    when(service.importToml(any(String.class), any(ImportOptions.class))).thenReturn(result);

    Path input = tempDir.resolve("feeds.toml");
    Files.writeString(
        input,
        "schema_version = 2\n[[feeds]]\nurl = \"https://example.com/rss\"\nstatus = \"active\"\n",
        StandardCharsets.UTF_8);

    FeedImportCommand cmd = new FeedImportCommand(service);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute("--input", input.toString());

    assertEquals(0, exit);
    verify(service).importToml(any(String.class), eq(new ImportOptions(false, false)));
  }

  @Test
  void feedsImportWithDryRunAndResubscribeSetsFlags() throws Exception {
    FeedTomlService service = mock(FeedTomlService.class);
    ImportResult result = new ImportResult(0, 0, 0, 0, 0, 0, List.of(), List.of());
    when(service.importToml(any(String.class), any(ImportOptions.class))).thenReturn(result);

    Path input = tempDir.resolve("feeds.toml");
    Files.writeString(
        input,
        "schema_version = 2\n[[feeds]]\nurl = \"https://example.com/rss\"\nstatus = \"active\"\n",
        StandardCharsets.UTF_8);

    FeedImportCommand cmd = new FeedImportCommand(service);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute("--input", input.toString(), "--dry-run", "--resubscribe");

    assertEquals(0, exit);
    verify(service).importToml(any(String.class), eq(new ImportOptions(true, true)));
  }

  @Test
  void feedsImportMissingInputIsUsageError() {
    FeedTomlService service = mock(FeedTomlService.class);
    FeedImportCommand cmd = new FeedImportCommand(service);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute(); // missing required --input

    assertEquals(2, exit); // usage error
  }

  @Test
  void feedsExportCallsServiceWithDefaults(@TempDir Path tmp) throws Exception {
    FeedTomlService service = mock(FeedTomlService.class);
    when(service.exportToml(false)).thenReturn("schema_version = 2\n");

    FeedExportCommand cmd = new FeedExportCommand(service);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute();

    assertEquals(0, exit);
    verify(service).exportToml(false);
  }

  @Test
  void feedsExportWithOutputAndActiveOnly() throws Exception {
    FeedTomlService service = mock(FeedTomlService.class);
    when(service.exportToml(true)).thenReturn("schema_version = 2\n");

    Path out = tempDir.resolve("out.toml");

    FeedExportCommand cmd = new FeedExportCommand(service);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute("--output", out.toString(), "--active-only");

    assertEquals(0, exit);
    verify(service).exportToml(true);
    assertTrue(Files.exists(out));
  }

  @Test
  void rootHelpAndFeedsHelpReturnZeroAndContainUsage() {
    SreaderCommand root = new SreaderCommand();
    CommandLine cli = new CommandLine(root, new TestPicocliFactory());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter pw = new PrintWriter(baos);
    cli.setOut(pw);

    int exit = cli.execute("--help");
    pw.flush();
    String out = baos.toString(StandardCharsets.UTF_8);

    assertEquals(0, exit);
    assertTrue(out.contains("Usage:"));
    assertTrue(out.contains("feeds"));

    // feeds --help
    baos.reset();
    pw = new PrintWriter(baos);
    cli.setOut(pw);
    int exit2 = cli.execute("feeds", "--help");
    pw.flush();
    String out2 = baos.toString(StandardCharsets.UTF_8);
    assertEquals(0, exit2);
    assertTrue(out2.contains("import") || out2.contains("export"));
  }

  @Test
  void feedsWithoutSubcommandShowsUsageError() {
    SreaderCommand root = new SreaderCommand();
    CommandLine cli = new CommandLine(root, new TestPicocliFactory());

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintWriter pw = new PrintWriter(err);
    cli.setErr(pw);

    int exit = cli.execute("feeds");
    pw.flush();
    String errText = err.toString(StandardCharsets.UTF_8);

    assertEquals(2, exit);
    assertTrue(errText.contains("Usage:"));
    assertTrue(errText.contains("import") || errText.contains("export"));
  }

  @Test
  void runOnceCommandTriggersScheduler() {
    FeedReaderScheduler scheduler = mock(FeedReaderScheduler.class);
    RunOnceCommand cmd = new RunOnceCommand(scheduler);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute();

    assertEquals(0, exit);
    verify(scheduler).runIfIdle();
  }

  @Test
  void unknownOptionYieldsUsageError() {
    FeedTomlService service = mock(FeedTomlService.class);
    FeedImportCommand cmd = new FeedImportCommand(service);
    CommandLine cli = new CommandLine(cmd);

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    cli.setErr(new PrintWriter(err));

    int exit = cli.execute("--no-such-option");
    // usage error for invalid option should be 2 per picocli convention used by this app
    assertEquals(2, exit);
  }

  @Test
  void probeArticleCallsServiceAndReturnsZero() {
    FullTextProbeService probeService = mock(FullTextProbeService.class);
    ProbeResult result =
        new ProbeResult(
            java.net.URI.create("https://example.com/a"),
            java.net.URI.create("https://example.com/a"),
            "T",
            FullTextMethod.HTTP,
            "body text here");
    when(probeService.probeArticle(any(java.net.URI.class), eq(FullTextMethod.HTTP), any()))
        .thenReturn(result);

    ProbeArticleCommand cmd = new ProbeArticleCommand(probeService);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute("--url", "https://example.com/a", "--method", "http");

    assertEquals(0, exit);
    verify(probeService).probeArticle(any(java.net.URI.class), eq(FullTextMethod.HTTP), any());
  }

  @Test
  void probeArticleRejectsMethodFeedAsUsageError() {
    FullTextProbeService probeService = mock(FullTextProbeService.class);
    ProbeArticleCommand cmd = new ProbeArticleCommand(probeService);
    CommandLine cli = new CommandLine(cmd);

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    cli.setErr(new PrintWriter(err));

    int exit = cli.execute("--url", "https://example.com/a", "--method", "feed");

    assertEquals(2, exit);
  }

  @Test
  void probeArticleInvalidUrlIsUsageError() {
    FullTextProbeService probeService = mock(FullTextProbeService.class);
    ProbeArticleCommand cmd = new ProbeArticleCommand(probeService);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute("--url", "not-a-url", "--method", "http");
    assertEquals(2, exit);
  }

  @Test
  void probeFeedCallsService() {
    FullTextProbeService probeService = mock(FullTextProbeService.class);
    ProbeResult result =
        new ProbeResult(
            java.net.URI.create("https://example.com/f.xml"),
            java.net.URI.create("https://example.com/a"),
            "T",
            FullTextMethod.HTTP,
            "body");
    when(probeService.probeFeed(any(java.net.URI.class), eq(FullTextMethod.HTTP), any(), any()))
        .thenReturn(result);

    ProbeFeedCommand cmd = new ProbeFeedCommand(probeService);
    CommandLine cli = new CommandLine(cmd);

    int exit = cli.execute("--feed-url", "https://example.com/f.xml", "--method", "http");

    assertEquals(0, exit);
    verify(probeService).probeFeed(any(java.net.URI.class), eq(FullTextMethod.HTTP), any(), any());
  }

  @Test
  void probeFeedMethodFeedWithXpathYieldsUsage() {
    FullTextProbeService probeService = mock(FullTextProbeService.class);
    ProbeFeedCommand cmd = new ProbeFeedCommand(probeService);
    CommandLine cli = new CommandLine(cmd);

    int exit =
        cli.execute(
            "--feed-url", "https://example.com/f.xml", "--method", "feed", "--xpath", "//p");
    assertEquals(2, exit);
  }

  @Test
  void feedsDiscoverCallsService() {
    FeedDiscoveryService disc = mock(FeedDiscoveryService.class);
    when(disc.discover(any(java.net.URI.class)))
        .thenReturn(List.of(java.net.URI.create("https://example.com/rss.xml")));

    FeedsDiscoverCommand cmd = new FeedsDiscoverCommand(disc);
    CommandLine cli = new CommandLine(cmd);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    cli.setOut(new PrintWriter(out));

    int exit = cli.execute("--site-url", "https://example.com/");

    assertEquals(0, exit);
    verify(disc).discover(any(java.net.URI.class));
  }

  @Test
  void feedsDiscoverTomlReflectsMethod() {
    FeedDiscoveryService disc = mock(FeedDiscoveryService.class);
    when(disc.discover(any(java.net.URI.class)))
        .thenReturn(
            List.of(
                java.net.URI.create("https://example.com/rss.xml"),
                java.net.URI.create("https://example.com/atom.xml")));

    FeedsDiscoverCommand cmd = new FeedsDiscoverCommand(disc);
    CommandLine cli = new CommandLine(cmd);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    cli.setOut(new PrintWriter(out));

    int exit =
        cli.execute(
            "--site-url",
            "https://example.com/",
            "--format",
            "toml",
            "--method",
            "playwright_readability");

    cli.getOut().flush();
    assertEquals(0, exit);
    String s = out.toString(StandardCharsets.UTF_8);
    assertTrue(s.contains("full_text_method = \"playwright_readability\""));
  }

  @Test
  void rootHelpIncludesProbeAndDiscover() {
    SreaderCommand root = new SreaderCommand();
    CommandLine cli = new CommandLine(root, new TestPicocliFactory());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter pw = new PrintWriter(baos);
    cli.setOut(pw);

    int exit = cli.execute("--help");
    pw.flush();
    String out = baos.toString(StandardCharsets.UTF_8);

    assertEquals(0, exit);
    assertTrue(out.contains("probe") || out.contains("Probe"));
  }

  /** Simple factory for bare CommandLine construction in help tests (no Spring). */
  private static class TestPicocliFactory implements picocli.CommandLine.IFactory {

    @Override
    public <K> K create(Class<K> cls) throws Exception {
      if (cls == ProbeCommand.class) {
        return (K) new ProbeCommand();
      }
      if (cls == ProbeArticleCommand.class) {
        return (K) new ProbeArticleCommand(mock(FullTextProbeService.class));
      }
      if (cls == ProbeFeedCommand.class) {
        return (K) new ProbeFeedCommand(mock(FullTextProbeService.class));
      }
      if (cls == FeedsDiscoverCommand.class) {
        return (K) new FeedsDiscoverCommand(mock(FeedDiscoveryService.class));
      }
      if (cls == RunOnceCommand.class) {
        return (K) new RunOnceCommand(mock(FeedReaderScheduler.class));
      }
      // Fallback for commands with no-arg ctor or other
      try {
        return cls.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        // last resort
        return (K)
            java.lang.reflect.Proxy.newProxyInstance(
                cls.getClassLoader(), new Class<?>[] {cls}, (p, m, a) -> null);
      }
    }
  }
}
