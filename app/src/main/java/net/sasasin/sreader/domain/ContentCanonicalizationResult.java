package net.sasasin.sreader.domain;

import java.util.Objects;

/**
 * Immutable snapshot of a canonicalization run, grouped by scan / group / database / file counters.
 */
public record ContentCanonicalizationResult(
    ScanSummary scan, GroupSummary groups, DatabaseSummary database, FileSummary files) {

  public ContentCanonicalizationResult {
    Objects.requireNonNull(scan, "scan must not be null");
    Objects.requireNonNull(groups, "groups must not be null");
    Objects.requireNonNull(database, "database must not be null");
    Objects.requireNonNull(files, "files must not be null");
  }

  public static ContentCanonicalizationResult empty() {
    return new ContentCanonicalizationResult(
        ScanSummary.empty(), GroupSummary.empty(), DatabaseSummary.empty(), FileSummary.empty());
  }

  public boolean hasFailures() {
    return groups.failedGroups() > 0 || files.failedFiles() > 0;
  }

  public record ScanSummary(int scannedRows, int unchangedRows) {
    public ScanSummary {
      scannedRows = requireNonNegative("scannedRows", scannedRows);
      unchangedRows = requireNonNegative("unchangedRows", unchangedRows);
    }

    public static ScanSummary empty() {
      return new ScanSummary(0, 0);
    }
  }

  public record GroupSummary(
      int renameGroups, int mergeGroups, int feedConflictGroups, int failedGroups) {
    public GroupSummary {
      renameGroups = requireNonNegative("renameGroups", renameGroups);
      mergeGroups = requireNonNegative("mergeGroups", mergeGroups);
      feedConflictGroups = requireNonNegative("feedConflictGroups", feedConflictGroups);
      failedGroups = requireNonNegative("failedGroups", failedGroups);
    }

    public static GroupSummary empty() {
      return new GroupSummary(0, 0, 0, 0);
    }

    /** Changed groups that were planned (rename or merge). Not a stored counter. */
    public int processedGroups() {
      return renameGroups + mergeGroups;
    }
  }

  public record DatabaseSummary(
      int deletedContentHeaders, int deletedFullTexts, int deletedExportHistories) {
    public DatabaseSummary {
      deletedContentHeaders = requireNonNegative("deletedContentHeaders", deletedContentHeaders);
      deletedFullTexts = requireNonNegative("deletedFullTexts", deletedFullTexts);
      deletedExportHistories = requireNonNegative("deletedExportHistories", deletedExportHistories);
    }

    public static DatabaseSummary empty() {
      return new DatabaseSummary(0, 0, 0);
    }
  }

  public record FileSummary(int deletedFiles, int missingFiles, int failedFiles) {
    public FileSummary {
      deletedFiles = requireNonNegative("deletedFiles", deletedFiles);
      missingFiles = requireNonNegative("missingFiles", missingFiles);
      failedFiles = requireNonNegative("failedFiles", failedFiles);
    }

    public static FileSummary empty() {
      return new FileSummary(0, 0, 0);
    }
  }

  private static int requireNonNegative(String name, int value) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must be non-negative: " + value);
    }
    return value;
  }
}
