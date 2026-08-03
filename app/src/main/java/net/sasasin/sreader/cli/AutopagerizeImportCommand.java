package net.sasasin.sreader.cli;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportException;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportOptions;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportReport;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "import",
    description = {
      "Import a local AutoPagerize items_all.json into PostgreSQL as an immutable dataset.",
      "Does not fetch URLs. SHA-256 is computed from the raw file bytes before parse."
    },
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class AutopagerizeImportCommand implements Callable<Integer> {

  private final AutoPagerizeImportService importService;

  @Option(
      names = {"--input", "-i"},
      paramLabel = "<PATH>",
      description = "Path to local items_all.json (required)",
      required = true)
  private Path input;

  @Option(
      names = "--dry-run",
      description = "Parse and validate only; do not create dataset rows or change active pointer")
  private boolean dryRun;

  @Option(
      names = "--no-activate",
      description = "Save dataset/rules/rejections but leave the active pointer unchanged")
  private boolean noActivate;

  @Option(
      names = "--strict",
      description = "Fail without DB changes if any rule is rejected (or existing has rejections)")
  private boolean strict;

  @Option(
      names = "--source-uri",
      paramLabel = "<URI>",
      description = "Optional provenance metadata only (never fetched)")
  private String sourceUri;

  public AutopagerizeImportCommand(AutoPagerizeImportService importService) {
    this.importService = importService;
  }

  @Override
  public Integer call() {
    AutoPagerizeImportOptions options =
        new AutoPagerizeImportOptions(dryRun, noActivate, strict, sourceUri);
    try {
      AutoPagerizeImportReport report = importService.importFile(input, options);
      printReport(report);
      return report.success() ? CliExitCodes.SUCCESS : CliExitCodes.EXECUTION_ERROR;
    } catch (AutoPagerizeImportException e) {
      System.err.println("autopagerize import failed: " + e.getMessage());
      return CliExitCodes.EXECUTION_ERROR;
    }
  }

  private static void printReport(AutoPagerizeImportReport report) {
    System.out.println("format=" + report.format());
    System.out.println("source_filename=" + nullToEmpty(report.sourceFilename()));
    System.out.println("source_uri=" + nullToEmpty(report.sourceUri()));
    System.out.println("source_sha256=" + report.sourceSha256());
    System.out.println("importer_version=" + report.importerVersion());
    System.out.println("input_count=" + report.inputCount());
    System.out.println("accepted_count=" + report.acceptedCount());
    System.out.println("rejected_count=" + report.rejectedCount());
    System.out.println("warning_count=" + report.warningCount());
    if (!report.warningReasonCounts().isEmpty()) {
      System.out.println("warning_reasons:");
      for (Map.Entry<String, Integer> entry : report.warningReasonCounts().entrySet()) {
        System.out.println("  " + entry.getKey() + "=" + entry.getValue());
      }
    }
    System.out.println("duplicate_diagnostic_count=" + report.duplicateDiagnosticCount());
    System.out.println("dry_run=" + report.dryRun());
    System.out.println("strict=" + report.strict());
    System.out.println("activated=" + report.activated());
    System.out.println("reused_existing_dataset=" + report.reusedExistingDataset());
    System.out.println("success=" + report.success());
    System.out.println(
        "dataset_id=" + (report.datasetId() == null ? "" : report.datasetId().toString()));
    if (!report.rejectionReasonCounts().isEmpty()) {
      System.out.println("rejection_reasons:");
      for (Map.Entry<String, Integer> entry : report.rejectionReasonCounts().entrySet()) {
        System.out.println("  " + entry.getKey() + "=" + entry.getValue());
      }
    }
    for (String message : report.messages()) {
      System.out.println("message=" + message);
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
