package net.sasasin.sreader.service;

import java.util.Objects;
import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.DatabaseSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.FileSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.GroupSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.ScanSummary;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;

/**
 * Mutable counters for one {@code canonicalize()} invocation. Not a Spring bean; not thread-safe.
 * Call {@link #snapshot()} at operation boundaries to obtain an immutable result.
 */
final class ContentCanonicalizationResultAccumulator {

  private int scannedRows;
  private int unchangedRows;
  private int renameGroups;
  private int mergeGroups;
  private int feedConflictGroups;
  private int failedGroups;
  private int deletedContentHeaders;
  private int deletedFullTexts;
  private int deletedExportHistories;
  private int deletedFiles;
  private int missingFiles;
  private int failedFiles;

  void addScannedRows(int rows) {
    scannedRows = addExact("scannedRows", scannedRows, rows);
  }

  void addUnchangedRows(int rows) {
    unchangedRows = addExact("unchangedRows", unchangedRows, rows);
  }

  void recordPlannedGroup(ContentCanonicalizationPlan plan) {
    Objects.requireNonNull(plan, "plan must not be null");
    if (plan.merge()) {
      mergeGroups = Math.addExact(mergeGroups, 1);
    } else {
      renameGroups = Math.addExact(renameGroups, 1);
    }
    if (plan.feedConflict()) {
      feedConflictGroups = Math.addExact(feedConflictGroups, 1);
    }
  }

  void addMergeCounts(ContentCanonicalizationMaintenanceRepository.MergeCounts counts) {
    Objects.requireNonNull(counts, "counts must not be null");
    deletedContentHeaders =
        addExact("deletedContentHeaders", deletedContentHeaders, counts.deletedHeaders());
    deletedFullTexts = addExact("deletedFullTexts", deletedFullTexts, counts.deletedFullTexts());
    deletedExportHistories =
        addExact("deletedExportHistories", deletedExportHistories, counts.deletedExportHistories());
  }

  void addFileSummary(FileSummary files) {
    Objects.requireNonNull(files, "files must not be null");
    deletedFiles = addExact("deletedFiles", deletedFiles, files.deletedFiles());
    missingFiles = addExact("missingFiles", missingFiles, files.missingFiles());
    failedFiles = addExact("failedFiles", failedFiles, files.failedFiles());
  }

  void recordFailedGroup() {
    failedGroups = Math.addExact(failedGroups, 1);
  }

  int processedGroups() {
    return Math.addExact(renameGroups, mergeGroups);
  }

  ContentCanonicalizationResult snapshot() {
    return new ContentCanonicalizationResult(
        new ScanSummary(scannedRows, unchangedRows),
        new GroupSummary(renameGroups, mergeGroups, feedConflictGroups, failedGroups),
        new DatabaseSummary(deletedContentHeaders, deletedFullTexts, deletedExportHistories),
        new FileSummary(deletedFiles, missingFiles, failedFiles));
  }

  private static int addExact(String name, int current, int delta) {
    if (delta < 0) {
      throw new IllegalArgumentException(name + " delta must be non-negative: " + delta);
    }
    if (delta == 0) {
      return current;
    }
    return Math.addExact(current, delta);
  }
}
