package net.sasasin.sreader.service;

import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Removes stale content text files after a successful DB merge. */
final class ContentCanonicalizationFileCleaner {
  private static final Logger logger =
      LoggerFactory.getLogger(ContentCanonicalizationFileCleaner.class);
  private final ContentTextFileStore fileStore;

  ContentCanonicalizationFileCleaner(ContentTextFileStore fileStore) {
    this.fileStore = fileStore;
  }

  ContentCanonicalizationResult clean(
      ContentCanonicalizationPlan plan, ContentCanonicalizationResult result) {
    for (String id : plan.memberIds()) {
      ContentTextFileStore.DeleteResult fileResult = fileStore.deleteForHeaderId(id);
      result =
          switch (fileResult.status()) {
            case DELETED -> result.withFileResult(1, 0, 0);
            case MISSING -> result.withFileResult(0, 1, 0);
            case FAILED -> result.withFileResult(0, 0, 1);
          };
      if (fileResult.status() == ContentTextFileStore.Status.FAILED) {
        logger.error("Could not delete stale content text file for {}: {}", id, fileResult.error());
      }
    }
    return result;
  }
}
