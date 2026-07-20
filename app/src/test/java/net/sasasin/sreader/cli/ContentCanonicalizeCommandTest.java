package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
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
  void explicitDryRunUsesDryRunMode() {
    ContentCanonicalizationMaintenanceService service = mock();
    when(service.canonicalize(any())).thenReturn(ContentCanonicalizationResult.empty());
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      assertThat(cli.execute("--dry-run")).isZero();
    } finally {
      System.setOut(originalOut);
    }

    verify(service)
        .canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(null, 100, null, false));
    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("mode=dry-run");
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
  void rejectsConflictingModesWithoutCallingService() {
    ContentCanonicalizationMaintenanceService service = mock();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    CommandLine applyThenDryRun = new CommandLine(new ContentCanonicalizeCommand(service));
    applyThenDryRun.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));
    assertThat(applyThenDryRun.execute("--apply", "--dry-run")).isEqualTo(2);
    assertThat(err.toString(StandardCharsets.UTF_8))
        .containsIgnoringCase("mutually exclusive")
        .contains("--apply")
        .contains("--dry-run");
    verifyNoInteractions(service);

    ByteArrayOutputStream err2 = new ByteArrayOutputStream();
    CommandLine dryRunThenApply = new CommandLine(new ContentCanonicalizeCommand(service));
    dryRunThenApply.setErr(new PrintWriter(err2, true, StandardCharsets.UTF_8));
    assertThat(dryRunThenApply.execute("--dry-run", "--apply")).isEqualTo(2);
    assertThat(err2.toString(StandardCharsets.UTF_8))
        .containsIgnoringCase("mutually exclusive")
        .contains("--apply")
        .contains("--dry-run");
    verifyNoInteractions(service);
  }

  @Test
  void invalidNumbersRemainExecutionErrors() {
    ContentCanonicalizationMaintenanceService service = mock();
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    assertThat(cli.execute("--batch-size", "0")).isEqualTo(1);
    assertThat(cli.execute("--limit", "0")).isEqualTo(1);
  }

  @Test
  void executionModeOptionsDefensiveInvariantRejectsBothFlags() {
    ContentCanonicalizeCommand.ExecutionModeOptions options =
        new ContentCanonicalizeCommand.ExecutionModeOptions();
    options.dryRun = true;
    options.apply = true;
    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, options::mode);
  }

  @Test
  void helpShowsExecutionModeGroup() {
    ContentCanonicalizationMaintenanceService service = mock();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));
    cli.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));

    assertThat(cli.execute("--help")).isZero();
    String help = out.toString(StandardCharsets.UTF_8);
    assertThat(help).contains("Execution mode:");
    assertThat(help).contains("--dry-run");
    assertThat(help).contains("--apply");
    // Flat Options section must not also list the group members a second time as root options.
    assertThat(help).doesNotContain("Options:\n  --dry-run");
  }

  private ContentCanonicalizationResult result() {
    return new ContentCanonicalizationResult(
        new ScanSummary(2, 1),
        new GroupSummary(0, 1, 0, 0),
        new DatabaseSummary(1, 1, 1),
        new FileSummary(1, 0, 0));
  }
}
