package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.sasasin.sreader.domain.AutoPagerizeDatasetSummary;
import net.sasasin.sreader.domain.AutoPagerizeFormats;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportException;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportOptions;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportReport;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AutopagerizeCommandsTest {

  @TempDir Path tempDir;

  @Test
  void importCallsServiceWithFlags() throws Exception {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    when(service.importFile(any(), any()))
        .thenReturn(
            new AutoPagerizeImportReport(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "items.json",
                "file:///x",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                1,
                2,
                1,
                1,
                0,
                0,
                true,
                false,
                false,
                false,
                true,
                null,
                Map.of("MISSING_URL", 1),
                Map.of("INVALID_CREATED_AT", 1),
                List.of("dry-run: no database changes")));

    Path input = tempDir.resolve("items.json");
    Files.writeString(input, "[]", StandardCharsets.UTF_8);

    AutopagerizeImportCommand cmd = new AutopagerizeImportCommand(service);
    CommandLine cli = new CommandLine(cmd);

    PrintStream originalOut = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
    try {
      int exit =
          cli.execute(
              "--input",
              input.toString(),
              "--dry-run",
              "--no-activate",
              "--strict",
              "--source-uri",
              "file:///x");
      assertThat(exit).isZero();
    } finally {
      System.setOut(originalOut);
    }

    verify(service)
        .importFile(
            any(Path.class), eq(new AutoPagerizeImportOptions(true, true, true, "file:///x")));
    assertThat(baos.toString(StandardCharsets.UTF_8)).contains("dry_run=true");
    assertThat(baos.toString(StandardCharsets.UTF_8)).contains("INVALID_CREATED_AT=1");
  }

  @Test
  void importUnsuccessfulReportReturnsExecutionError() throws Exception {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    when(service.importFile(any(), any()))
        .thenReturn(
            new AutoPagerizeImportReport(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "items.json",
                null,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                1,
                2,
                1,
                1,
                0,
                0,
                false,
                false,
                false,
                true,
                false,
                null,
                Map.of(),
                Map.of(),
                List.of("strict mode")));
    Path input = tempDir.resolve("items.json");
    Files.writeString(input, "[]", StandardCharsets.UTF_8);
    int exit =
        new CommandLine(new AutopagerizeImportCommand(service))
            .execute("--input", input.toString());
    assertThat(exit).isEqualTo(1);
  }

  @Test
  void importFailureReturnsExecutionError() throws Exception {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    when(service.importFile(any(), any()))
        .thenThrow(new AutoPagerizeImportException("No accepted rules"));

    Path input = tempDir.resolve("items.json");
    Files.writeString(input, "[]", StandardCharsets.UTF_8);

    AutopagerizeImportCommand cmd = new AutopagerizeImportCommand(service);
    int exit = new CommandLine(cmd).execute("--input", input.toString());
    assertThat(exit).isEqualTo(1);
  }

  @Test
  void importMissingInputIsUsageError() {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    int exit = new CommandLine(new AutopagerizeImportCommand(service)).execute();
    assertThat(exit).isEqualTo(2);
  }

  @Test
  void listEmptyDatasets() {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    when(service.findActiveDatasetId()).thenReturn(Optional.empty());
    when(service.listDatasets()).thenReturn(List.of());
    AutopagerizeDatasetsListCommand cmd = new AutopagerizeDatasetsListCommand(service);
    PrintStream originalOut = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
    try {
      assertThat(new CommandLine(cmd).execute()).isZero();
    } finally {
      System.setOut(originalOut);
    }
    assertThat(baos.toString(StandardCharsets.UTF_8)).contains("No AutoPagerize datasets");
  }

  @Test
  void listPrintsActiveMarker() {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    when(service.findActiveDatasetId()).thenReturn(Optional.of(2L));
    when(service.listDatasets())
        .thenReturn(
            List.of(
                new AutoPagerizeDatasetSummary(
                    2L,
                    "short",
                    null,
                    "  ",
                    "shortsha",
                    1,
                    OffsetDateTime.parse("2026-01-02T00:00:00Z"),
                    1,
                    1,
                    0),
                new AutoPagerizeDatasetSummary(
                    1L,
                    AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                    "a.json",
                    "file:///a",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    1,
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    2,
                    1,
                    1)));

    AutopagerizeDatasetsListCommand cmd = new AutopagerizeDatasetsListCommand(service);
    PrintStream originalOut = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
    try {
      assertThat(new CommandLine(cmd).execute()).isZero();
    } finally {
      System.setOut(originalOut);
    }
    String out = baos.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("ACTIVE");
    assertThat(out).contains("a.json");
    assertThat(out).contains("shortsha");
    assertThat(out).contains("*");
  }

  @Test
  void activateCallsService() {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    when(service.activateDataset(7L)).thenReturn(7L);
    AutopagerizeDatasetsActivateCommand cmd = new AutopagerizeDatasetsActivateCommand(service);
    assertThat(new CommandLine(cmd).execute("--dataset-id", "7")).isZero();
    verify(service).activateDataset(7L);
  }

  @Test
  void activateUnknownIdReturnsExecutionError() {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    when(service.activateDataset(9L)).thenThrow(new IllegalArgumentException("does not exist: 9"));
    AutopagerizeDatasetsActivateCommand cmd = new AutopagerizeDatasetsActivateCommand(service);
    assertThat(new CommandLine(cmd).execute("--dataset-id", "9")).isEqualTo(1);
  }

  @Test
  void autopagerizeHelpMentionsImportAndDatasets() {
    AutoPagerizeImportService service = mock(AutoPagerizeImportService.class);
    CommandLine cli =
        new CommandLine(
            new AutopagerizeCommand(),
            new CommandLine.IFactory() {
              @Override
              public <K> K create(Class<K> cls) throws Exception {
                if (cls == AutopagerizeImportCommand.class) {
                  return cls.cast(new AutopagerizeImportCommand(service));
                }
                if (cls == AutopagerizeDatasetsCommand.class) {
                  return cls.cast(new AutopagerizeDatasetsCommand());
                }
                if (cls == AutopagerizeDatasetsListCommand.class) {
                  return cls.cast(new AutopagerizeDatasetsListCommand(service));
                }
                if (cls == AutopagerizeDatasetsActivateCommand.class) {
                  return cls.cast(new AutopagerizeDatasetsActivateCommand(service));
                }
                return CommandLine.defaultFactory().create(cls);
              }
            });
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    cli.setOut(new java.io.PrintWriter(baos, true));
    assertThat(cli.execute("--help")).isZero();
    String out = baos.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("import");
    assertThat(out).contains("datasets");
  }
}
