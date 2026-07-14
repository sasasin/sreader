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
