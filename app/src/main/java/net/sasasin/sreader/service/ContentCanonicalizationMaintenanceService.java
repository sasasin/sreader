package net.sasasin.sreader.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationMember;
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

  private final ArticleUrlCanonicalizer canonicalizer;
  private final ContentCanonicalizationMaintenanceRepository repository;
  private final ContentTextFileStore fileStore;

  public ContentCanonicalizationMaintenanceService(
      ArticleUrlCanonicalizer canonicalizer,
      ContentCanonicalizationMaintenanceRepository repository,
      ContentTextFileStore fileStore) {
    this.canonicalizer = canonicalizer;
    this.repository = repository;
    this.fileStore = fileStore;
  }

  public ContentCanonicalizationResult canonicalize(Options options) {
    ContentCanonicalizationResult result = emptyResult();
    String after = null;
    Set<String> processed = new HashSet<>();
    while (options.limit() == null || result.processedGroups() < options.limit()) {
      List<String> candidates =
          repository.findCandidateCanonicalUrls(options.host(), after, options.batchSize());
      if (candidates.isEmpty()) {
        return result;
      }
      after = candidates.getLast();
      for (String candidate : candidates) {
        result = addScanned(result);
        String normalized = canonicalizer.canonicalize(URI.create(candidate)).toString();
        if (!processed.add(normalized)) {
          continue;
        }
        ContentCanonicalizationGroup group = repository.loadGroup(normalized);
        List<ContentCanonicalizationMember> matchingMembers =
            group.members().stream()
                .filter(
                    member ->
                        canonicalizer
                            .canonicalize(URI.create(member.canonicalUrl()))
                            .toString()
                            .equals(normalized))
                .toList();
        group =
            new ContentCanonicalizationGroup(
                normalized, matchingMembers, group.exportHistoryRows());
        if (!needsChange(group)) {
          result = addUnchanged(result, matchingMembers.size());
          continue;
        }
        ContentCanonicalizationPlan plan = plan(group);
        result = addPlan(result, plan);
        if (options.apply()) {
          try {
            ContentCanonicalizationMaintenanceRepository.MergeCounts counts =
                repository.merge(plan);
            result = addMergeCounts(result, counts);
            for (String id : plan.memberIds()) {
              ContentTextFileStore.DeleteResult fileResult = fileStore.deleteForHeaderId(id);
              result = addFileResult(result, fileResult);
              if (fileResult.status() == ContentTextFileStore.Status.FAILED) {
                logger.error(
                    "Could not delete stale content text file for {}: {}", id, fileResult.error());
              }
            }
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

  private boolean needsChange(ContentCanonicalizationGroup group) {
    return group.members().size() > 1
        || group.members().stream()
            .anyMatch(member -> !member.canonicalUrl().equals(group.canonicalUrl()));
  }

  private ContentCanonicalizationPlan plan(ContentCanonicalizationGroup group) {
    List<ContentCanonicalizationMember> members = group.members();
    Comparator<ContentCanonicalizationMember> oldest =
        Comparator.comparing(ContentCanonicalizationMember::createdAt)
            .thenComparing(ContentCanonicalizationMember::id);
    ContentCanonicalizationMember source =
        members.stream()
            .filter(member -> !member.sourceUrl().equals(group.canonicalUrl()))
            .min(oldest)
            .orElseGet(() -> members.stream().min(oldest).orElseThrow());
    ContentCanonicalizationMember fetch =
        members.stream()
            .max(
                Comparator.comparing(ContentCanonicalizationMember::updatedAt)
                    .thenComparing(ContentCanonicalizationMember::createdAt)
                    .thenComparing(ContentCanonicalizationMember::id))
            .orElseThrow();
    ContentCanonicalizationMember feed = members.stream().min(oldest).orElseThrow();
    ContentCanonicalizationMember title =
        members.stream()
            .filter(member -> !blank(member.title()))
            .max(
                Comparator.comparing(ContentCanonicalizationMember::updatedAt)
                    .thenComparing(ContentCanonicalizationMember::id))
            .orElse(feed);
    ContentCanonicalizationMember feedText =
        members.stream()
            .filter(member -> !blank(member.feedText()))
            .max(
                Comparator.comparingInt(
                        (ContentCanonicalizationMember member) -> member.feedText().length())
                    .thenComparing(ContentCanonicalizationMember::updatedAt)
                    .thenComparing(ContentCanonicalizationMember::id))
            .orElse(feed);
    OffsetDateTime publishedAt =
        members.stream()
            .map(ContentCanonicalizationMember::publishedAt)
            .filter(Objects::nonNull)
            .min(OffsetDateTime::compareTo)
            .orElse(null);
    ContentCanonicalizationMember values =
        new ContentCanonicalizationMember(
            feed.id(),
            feed.feedUrlId(),
            source.sourceUrl(),
            fetch.fetchUrl(),
            group.canonicalUrl(),
            title.title(),
            publishedAt,
            feedText.feedText(),
            members.stream()
                .map(ContentCanonicalizationMember::createdAt)
                .min(OffsetDateTime::compareTo)
                .orElseThrow(),
            fetch.updatedAt(),
            null,
            null,
            null,
            null);
    ContentCanonicalizationMember fullText =
        members.stream()
            .filter(member -> !blank(member.fullText()))
            .max(
                Comparator.comparingInt(
                        (ContentCanonicalizationMember member) -> member.fullText().length())
                    .thenComparing(ContentCanonicalizationMember::extractedAt)
                    .thenComparing(
                        ContentCanonicalizationMember::fullTextCreatedAt, Comparator.reverseOrder())
                    .thenComparing(ContentCanonicalizationMember::fullTextId))
            .orElse(null);
    boolean feedConflict =
        members.stream().map(ContentCanonicalizationMember::feedUrlId).distinct().count() > 1;
    return new ContentCanonicalizationPlan(
        group, values, fullText, HashIds.md5(group.canonicalUrl()), feedConflict);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private ContentCanonicalizationResult emptyResult() {
    return new ContentCanonicalizationResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private ContentCanonicalizationResult addScanned(ContentCanonicalizationResult r) {
    return new ContentCanonicalizationResult(
        r.scannedRows() + 1,
        r.unchangedRows(),
        r.renameGroups(),
        r.mergeGroups(),
        r.deletedContentHeaders(),
        r.deletedFullTexts(),
        r.deletedExportHistories(),
        r.feedConflictGroups(),
        r.processedGroups(),
        r.deletedFiles(),
        r.missingFiles(),
        r.failedFiles(),
        r.failedGroups());
  }

  private ContentCanonicalizationResult addUnchanged(ContentCanonicalizationResult r, int rows) {
    return new ContentCanonicalizationResult(
        r.scannedRows(),
        r.unchangedRows() + rows,
        r.renameGroups(),
        r.mergeGroups(),
        r.deletedContentHeaders(),
        r.deletedFullTexts(),
        r.deletedExportHistories(),
        r.feedConflictGroups(),
        r.processedGroups(),
        r.deletedFiles(),
        r.missingFiles(),
        r.failedFiles(),
        r.failedGroups());
  }

  private ContentCanonicalizationResult addPlan(
      ContentCanonicalizationResult r, ContentCanonicalizationPlan plan) {
    return new ContentCanonicalizationResult(
        r.scannedRows(),
        r.unchangedRows(),
        r.renameGroups() + (plan.merge() ? 0 : 1),
        r.mergeGroups() + (plan.merge() ? 1 : 0),
        r.deletedContentHeaders(),
        r.deletedFullTexts(),
        r.deletedExportHistories(),
        r.feedConflictGroups() + (plan.feedConflict() ? 1 : 0),
        r.processedGroups() + 1,
        r.deletedFiles(),
        r.missingFiles(),
        r.failedFiles(),
        r.failedGroups());
  }

  private ContentCanonicalizationResult addMergeCounts(
      ContentCanonicalizationResult r, ContentCanonicalizationMaintenanceRepository.MergeCounts c) {
    return new ContentCanonicalizationResult(
        r.scannedRows(),
        r.unchangedRows(),
        r.renameGroups(),
        r.mergeGroups(),
        r.deletedContentHeaders() + c.deletedHeaders(),
        r.deletedFullTexts() + c.deletedFullTexts(),
        r.deletedExportHistories() + c.deletedExportHistories(),
        r.feedConflictGroups(),
        r.processedGroups(),
        r.deletedFiles(),
        r.missingFiles(),
        r.failedFiles(),
        r.failedGroups());
  }

  private ContentCanonicalizationResult addFileResult(
      ContentCanonicalizationResult r, ContentTextFileStore.DeleteResult f) {
    return switch (f.status()) {
      case DELETED -> r.withFileResult(1, 0, 0);
      case MISSING -> r.withFileResult(0, 1, 0);
      case FAILED -> r.withFileResult(0, 0, 1);
    };
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
