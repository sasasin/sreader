package net.sasasin.sreader.cli;

import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.service.canonicalization.ContentCanonicalizationMaintenanceService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "canonicalize",
    description = "Merge stored content records that resolve to the same canonical URL.",
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class ContentCanonicalizeCommand implements java.util.concurrent.Callable<Integer> {

  private final ContentCanonicalizationMaintenanceService service;

  @ArgGroup(exclusive = true, multiplicity = "0..1", heading = "Execution mode:%n")
  private ExecutionModeOptions executionMode;

  @Option(
      names = "--host",
      paramLabel = "<HOSTNAME>",
      description = "Restrict processing to this host")
  private String host;

  @Option(
      names = "--batch-size",
      defaultValue = "100",
      paramLabel = "<N>",
      description = "Maximum candidate rows read at once (default: ${DEFAULT-VALUE})")
  private int batchSize;

  @Option(
      names = "--limit",
      paramLabel = "<N>",
      description = "Process at most this many changed groups")
  private Integer limit;

  public ContentCanonicalizeCommand(ContentCanonicalizationMaintenanceService service) {
    this.service = service;
  }

  @Override
  public Integer call() {
    CanonicalizationExecutionMode mode = executionMode();
    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(
                host, batchSize, limit, mode.apply()));
    print(result, mode.displayValue());
    return result.hasFailures() ? 1 : 0;
  }

  private CanonicalizationExecutionMode executionMode() {
    return executionMode == null ? CanonicalizationExecutionMode.DRY_RUN : executionMode.mode();
  }

  private void print(ContentCanonicalizationResult result, String mode) {
    var scan = result.scan();
    var groups = result.groups();
    var database = result.database();
    var files = result.files();
    System.out.printf("mode=%s%n", mode);
    System.out.printf("scanned_rows=%d%n", scan.scannedRows());
    System.out.printf("unchanged_rows=%d%n", scan.unchangedRows());
    System.out.printf("rename_groups=%d%n", groups.renameGroups());
    System.out.printf("merge_groups=%d%n", groups.mergeGroups());
    System.out.printf("duplicate_rows_to_delete=%d%n", database.deletedContentHeaders());
    System.out.printf("full_text_rows_to_delete=%d%n", database.deletedFullTexts());
    System.out.printf("export_history_rows_to_delete=%d%n", database.deletedExportHistories());
    System.out.printf("feed_conflict_groups=%d%n", groups.feedConflictGroups());
    System.out.printf("processed_groups=%d%n", groups.processedGroups());
    System.out.printf("deleted_files=%d%n", files.deletedFiles());
    System.out.printf("missing_files=%d%n", files.missingFiles());
    System.out.printf("failed_files=%d%n", files.failedFiles());
    System.out.printf("failed_groups=%d%n", groups.failedGroups());
  }

  /** CLI execution mode for content canonicalization (dry-run vs apply). */
  enum CanonicalizationExecutionMode {
    DRY_RUN(false, "dry-run"),
    APPLY(true, "apply");

    private final boolean apply;
    private final String displayValue;

    CanonicalizationExecutionMode(boolean apply, String displayValue) {
      this.apply = apply;
      this.displayValue = displayValue;
    }

    boolean apply() {
      return apply;
    }

    String displayValue() {
      return displayValue;
    }
  }

  /**
   * Optional exclusive group for {@code --dry-run} / {@code --apply}. When the group is omitted,
   * {@link CanonicalizationExecutionMode#DRY_RUN} is used.
   */
  static final class ExecutionModeOptions {

    @Option(
        names = "--dry-run",
        description = "Report changes without modifying the database or files (default mode)")
    boolean dryRun;

    @Option(names = "--apply", description = "Apply database changes and remove stale text files")
    boolean apply;

    CanonicalizationExecutionMode mode() {
      if (dryRun && apply) {
        throw new IllegalStateException("Picocli exclusive group invariant violated");
      }
      if (apply) {
        return CanonicalizationExecutionMode.APPLY;
      }
      return CanonicalizationExecutionMode.DRY_RUN;
    }
  }
}
