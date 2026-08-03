package net.sasasin.sreader.service.autopagerize;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Structured import outcome for CLI and tests. */
public record AutoPagerizeImportReport(
    String format,
    String sourceFilename,
    String sourceUri,
    String sourceSha256,
    int importerVersion,
    int inputCount,
    int acceptedCount,
    int rejectedCount,
    int warningCount,
    int duplicateDiagnosticCount,
    boolean dryRun,
    boolean activated,
    boolean reusedExistingDataset,
    boolean strict,
    boolean success,
    Long datasetId,
    Map<String, Integer> rejectionReasonCounts,
    List<String> messages) {

  public AutoPagerizeImportReport {
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(sourceSha256, "sourceSha256 must not be null");
    Objects.requireNonNull(rejectionReasonCounts, "rejectionReasonCounts must not be null");
    Objects.requireNonNull(messages, "messages must not be null");
    rejectionReasonCounts = Map.copyOf(rejectionReasonCounts);
    messages = List.copyOf(messages);
  }
}
