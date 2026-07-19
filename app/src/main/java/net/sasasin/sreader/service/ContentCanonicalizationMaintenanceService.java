package net.sasasin.sreader.service;

import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContentCanonicalizationMaintenanceService {

  private static final Logger logger =
      LoggerFactory.getLogger(ContentCanonicalizationMaintenanceService.class);

  private final ContentCanonicalizationCandidateScanner candidateScanner;
  private final ContentCanonicalizationPlanner planner;
  private final ContentCanonicalizationExecutor executor;
  private final ContentCanonicalizationFileCleaner fileCleaner;

  public ContentCanonicalizationMaintenanceService(
      ArticleUrlCanonicalizer canonicalizer,
      ContentCanonicalizationMaintenanceRepository repository,
      ContentTextFileStore fileStore) {
    this.candidateScanner = new ContentCanonicalizationCandidateScanner(canonicalizer, repository);
    this.planner = new ContentCanonicalizationPlanner();
    this.executor = new ContentCanonicalizationExecutor(repository);
    this.fileCleaner = new ContentCanonicalizationFileCleaner(fileStore);
  }

  public ContentCanonicalizationResult canonicalize(Options options) {
    ContentCanonicalizationResult result = ContentCanonicalizationResult.empty();
    ContentCanonicalizationCandidateScanner.Session scanner =
        candidateScanner.start(options.host(), options.batchSize());
    while (options.limit() == null || result.processedGroups() < options.limit()) {
      ContentCanonicalizationCandidateScanner.Page page = scanner.next();
      if (page.isFinished()) {
        return result;
      }
      for (int ignored = 0; ignored < page.scannedRows(); ignored++) {
        result = result.incrementScannedRows();
      }
      for (ContentCanonicalizationCandidateScanner.GroupCandidate candidate : page.groups()) {
        String normalized = candidate.normalizedUrl();
        var group = candidate.group();
        if (!planner.needsChange(group)) {
          result = result.addUnchangedRows(group.members().size());
          continue;
        }
        ContentCanonicalizationPlan plan = planner.plan(group);
        result = result.addPlannedGroup(plan.merge(), plan.feedConflict());
        if (options.apply()) {
          try {
            ContentCanonicalizationMaintenanceRepository.MergeCounts counts =
                executor.execute(plan);
            result =
                result.addMergedRows(
                    counts.deletedHeaders(),
                    counts.deletedFullTexts(),
                    counts.deletedExportHistories());
            result = fileCleaner.clean(plan, result);
          } catch (RuntimeException e) {
            logger.error("Could not canonicalize group {}", normalized, e);
            result = result.withFailedGroup();
          }
        }
        if (options.limit() != null && result.processedGroups() >= options.limit()) {
          return result;
        }
      }
    }
    return result;
  }

  public record Options(String host, int batchSize, Integer limit, boolean apply) {
    public Options {
      if (host != null && host.isBlank()) {
        host = null;
      }
      if (batchSize <= 0) {
        throw new IllegalArgumentException("batchSize must be positive");
      }
      if (limit != null && limit <= 0) {
        throw new IllegalArgumentException("limit must be positive");
      }
    }
  }
}
