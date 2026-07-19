package net.sasasin.sreader.domain;

public record ContentCanonicalizationResult(
    int scannedRows,
    int unchangedRows,
    int renameGroups,
    int mergeGroups,
    int deletedContentHeaders,
    int deletedFullTexts,
    int deletedExportHistories,
    int feedConflictGroups,
    int processedGroups,
    int deletedFiles,
    int missingFiles,
    int failedFiles,
    int failedGroups) {

  public static ContentCanonicalizationResult empty() {
    return new ContentCanonicalizationResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  public ContentCanonicalizationResult incrementScannedRows() {
    return new ContentCanonicalizationResult(
        scannedRows + 1,
        unchangedRows,
        renameGroups,
        mergeGroups,
        deletedContentHeaders,
        deletedFullTexts,
        deletedExportHistories,
        feedConflictGroups,
        processedGroups,
        deletedFiles,
        missingFiles,
        failedFiles,
        failedGroups);
  }

  public ContentCanonicalizationResult addUnchangedRows(int rows) {
    return new ContentCanonicalizationResult(
        scannedRows,
        unchangedRows + rows,
        renameGroups,
        mergeGroups,
        deletedContentHeaders,
        deletedFullTexts,
        deletedExportHistories,
        feedConflictGroups,
        processedGroups,
        deletedFiles,
        missingFiles,
        failedFiles,
        failedGroups);
  }

  public ContentCanonicalizationResult addPlannedGroup(boolean merge, boolean feedConflict) {
    return new ContentCanonicalizationResult(
        scannedRows,
        unchangedRows,
        renameGroups + (merge ? 0 : 1),
        mergeGroups + (merge ? 1 : 0),
        deletedContentHeaders,
        deletedFullTexts,
        deletedExportHistories,
        feedConflictGroups + (feedConflict ? 1 : 0),
        processedGroups + 1,
        deletedFiles,
        missingFiles,
        failedFiles,
        failedGroups);
  }

  public ContentCanonicalizationResult addMergedRows(
      int headers, int fullTexts, int exportHistories) {
    return new ContentCanonicalizationResult(
        scannedRows,
        unchangedRows,
        renameGroups,
        mergeGroups,
        deletedContentHeaders + headers,
        deletedFullTexts + fullTexts,
        deletedExportHistories + exportHistories,
        feedConflictGroups,
        processedGroups,
        deletedFiles,
        missingFiles,
        failedFiles,
        failedGroups);
  }

  public ContentCanonicalizationResult withFileResult(int deleted, int missing, int failed) {
    return new ContentCanonicalizationResult(
        scannedRows,
        unchangedRows,
        renameGroups,
        mergeGroups,
        deletedContentHeaders,
        deletedFullTexts,
        deletedExportHistories,
        feedConflictGroups,
        processedGroups,
        deletedFiles + deleted,
        missingFiles + missing,
        failedFiles + failed,
        failedGroups);
  }

  public ContentCanonicalizationResult withFailedGroup() {
    return new ContentCanonicalizationResult(
        scannedRows,
        unchangedRows,
        renameGroups,
        mergeGroups,
        deletedContentHeaders,
        deletedFullTexts,
        deletedExportHistories,
        feedConflictGroups,
        processedGroups,
        deletedFiles,
        missingFiles,
        failedFiles,
        failedGroups + 1);
  }

  public boolean hasFailures() {
    return failedFiles > 0 || failedGroups > 0;
  }
}
