package net.sasasin.sreader.cli;

import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.service.ContentCanonicalizationMaintenanceService;
import org.springframework.stereotype.Component;
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

  @Option(
      names = "--dry-run",
      description = "Report changes without modifying the database or files")
  private boolean dryRun;

  @Option(names = "--apply", description = "Apply database changes and remove stale text files")
  private boolean apply;

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
    if (dryRun && apply) {
      throw new picocli.CommandLine.ParameterException(
          new picocli.CommandLine(this), "--dry-run and --apply are mutually exclusive");
    }
    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(host, batchSize, limit, apply));
    print(result, apply ? "apply" : "dry-run");
    return result.hasFailures() ? 1 : 0;
  }

  private void print(ContentCanonicalizationResult result, String mode) {
    System.out.printf("mode=%s%n", mode);
    System.out.printf("scanned_rows=%d%n", result.scannedRows());
    System.out.printf("unchanged_rows=%d%n", result.unchangedRows());
    System.out.printf("rename_groups=%d%n", result.renameGroups());
    System.out.printf("merge_groups=%d%n", result.mergeGroups());
    System.out.printf("duplicate_rows_to_delete=%d%n", result.deletedContentHeaders());
    System.out.printf("full_text_rows_to_delete=%d%n", result.deletedFullTexts());
    System.out.printf("export_history_rows_to_delete=%d%n", result.deletedExportHistories());
    System.out.printf("feed_conflict_groups=%d%n", result.feedConflictGroups());
    System.out.printf("processed_groups=%d%n", result.processedGroups());
    System.out.printf("deleted_files=%d%n", result.deletedFiles());
    System.out.printf("missing_files=%d%n", result.missingFiles());
    System.out.printf("failed_files=%d%n", result.failedFiles());
    System.out.printf("failed_groups=%d%n", result.failedGroups());
  }
}
