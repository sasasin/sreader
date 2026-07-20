package net.sasasin.sreader.service.canonicalization;

import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.FileSummary;
import net.sasasin.sreader.service.text.ContentTextFileStore;
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

  FileSummary clean(ContentCanonicalizationPlan plan) {
    int deleted = 0;
    int missing = 0;
    int failed = 0;
    for (String id : plan.memberIds()) {
      ContentTextFileStore.DeleteResult fileResult = fileStore.deleteForHeaderId(id);
      switch (fileResult.status()) {
        case DELETED -> deleted++;
        case MISSING -> missing++;
        case FAILED -> {
          failed++;
          logger.error(
              "Could not delete stale content text file for {}: {}", id, fileResult.error());
        }
      }
    }
    return new FileSummary(deleted, missing, failed);
  }
}
