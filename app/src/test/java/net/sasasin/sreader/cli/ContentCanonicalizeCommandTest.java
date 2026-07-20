package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.DatabaseSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.FileSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.GroupSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.ScanSummary;
import net.sasasin.sreader.service.canonicalization.ContentCanonicalizationMaintenanceService;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ContentCanonicalizeCommandTest {

  @Test
  void defaultsToDryRunAndPrintsSummary() {
    ContentCanonicalizationMaintenanceService service = mock();
    when(service.canonicalize(any())).thenReturn(result());
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      assertThat(cli.execute("--host", "canonicalization.test", "--batch-size", "3")).isZero();
    } finally {
      System.setOut(originalOut);
    }

    verify(service)
        .canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(
                "canonicalization.test", 3, null, false));
    assertThat(captured.toString(StandardCharsets.UTF_8).trim().lines().toList())
        .containsExactly(
            "mode=dry-run",
            "scanned_rows=2",
            "unchanged_rows=1",
            "rename_groups=0",
            "merge_groups=1",
            "duplicate_rows_to_delete=1",
            "full_text_rows_to_delete=1",
            "export_history_rows_to_delete=1",
            "feed_conflict_groups=0",
            "processed_groups=1",
            "deleted_files=1",
            "missing_files=0",
            "failed_files=0",
            "failed_groups=0");
  }

  @Test
  void applyReturnsNonZeroForFailedFiles() {
    ContentCanonicalizationMaintenanceService service = mock();
    when(service.canonicalize(any()))
        .thenReturn(
            new ContentCanonicalizationResult(
                ScanSummary.empty(),
                GroupSummary.empty(),
                DatabaseSummary.empty(),
                new FileSummary(0, 0, 1)));
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    assertThat(cli.execute("--apply", "--limit", "1")).isEqualTo(1);
    verify(service)
        .canonicalize(new ContentCanonicalizationMaintenanceService.Options(null, 100, 1, true));
  }

  @Test
  void applyReturnsNonZeroForFailedGroups() {
    ContentCanonicalizationMaintenanceService service = mock();
    when(service.canonicalize(any()))
        .thenReturn(
            new ContentCanonicalizationResult(
                ScanSummary.empty(),
                new GroupSummary(0, 0, 0, 1),
                DatabaseSummary.empty(),
                FileSummary.empty()));
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    assertThat(cli.execute("--apply")).isEqualTo(1);
  }

  @Test
  void applyModeLabelIsPrinted() {
    ContentCanonicalizationMaintenanceService service = mock();
    when(service.canonicalize(any())).thenReturn(ContentCanonicalizationResult.empty());
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      assertThat(cli.execute("--apply")).isZero();
    } finally {
      System.setOut(originalOut);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("mode=apply");
  }

  @Test
  void rejectsConflictingModesAndInvalidNumbers() {
    ContentCanonicalizationMaintenanceService service = mock();
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    assertThat(cli.execute("--apply", "--dry-run")).isEqualTo(2);
    assertThat(cli.execute("--batch-size", "0")).isEqualTo(1);
    assertThat(cli.execute("--limit", "0")).isEqualTo(1);
  }

  private ContentCanonicalizationResult result() {
    return new ContentCanonicalizationResult(
        new ScanSummary(2, 1),
        new GroupSummary(0, 1, 0, 0),
        new DatabaseSummary(1, 1, 1),
        new FileSummary(1, 0, 0));
  }
}
