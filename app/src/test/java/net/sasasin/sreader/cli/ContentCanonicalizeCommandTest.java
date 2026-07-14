package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.service.ContentCanonicalizationMaintenanceService;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ContentCanonicalizeCommandTest {

  @Test
  void defaultsToDryRunAndPrintsSummary() {
    ContentCanonicalizationMaintenanceService service = mock();
    when(service.canonicalize(any())).thenReturn(result());
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    assertThat(cli.execute("--host", "canonicalization.test", "--batch-size", "3")).isZero();
    verify(service)
        .canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(
                "canonicalization.test", 3, null, false));
  }

  @Test
  void applyReturnsNonZeroForPartialFailure() {
    ContentCanonicalizationMaintenanceService service = mock();
    when(service.canonicalize(any()))
        .thenReturn(new ContentCanonicalizationResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0));
    CommandLine cli = new CommandLine(new ContentCanonicalizeCommand(service));

    assertThat(cli.execute("--apply", "--limit", "1")).isEqualTo(1);
    verify(service)
        .canonicalize(new ContentCanonicalizationMaintenanceService.Options(null, 100, 1, true));
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
    return new ContentCanonicalizationResult(2, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 0);
  }
}
