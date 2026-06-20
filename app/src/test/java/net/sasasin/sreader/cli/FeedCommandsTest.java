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
import net.sasasin.sreader.service.FeedTomlService;
import net.sasasin.sreader.service.FeedTomlService.ImportOptions;
import net.sasasin.sreader.service.FeedTomlService.ImportResult;
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
    CommandLine cli = new CommandLine(root);

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
}
