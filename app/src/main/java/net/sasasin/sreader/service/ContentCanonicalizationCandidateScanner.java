package net.sasasin.sreader.service;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sasasin.sreader.domain.ContentCanonicalizationCandidate;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;

/** Paginates canonicalization candidates and emits each normalized URL group once. */
final class ContentCanonicalizationCandidateScanner {

  private final ArticleUrlCanonicalizer canonicalizer;
  private final ContentCanonicalizationMaintenanceRepository repository;

  ContentCanonicalizationCandidateScanner(
      ArticleUrlCanonicalizer canonicalizer,
      ContentCanonicalizationMaintenanceRepository repository) {
    this.canonicalizer = canonicalizer;
    this.repository = repository;
  }

  Session start(String host, int batchSize) {
    return new Session(host, batchSize);
  }

  final class Session {

    private final String host;
    private final int batchSize;
    private final Set<String> processed = new HashSet<>();
    private String after;

    private Session(String host, int batchSize) {
      this.host = host;
      this.batchSize = batchSize;
    }

    Page next() {
      List<String> candidates = repository.findCandidateCanonicalUrls(host, after, batchSize);
      if (candidates.isEmpty()) {
        return Page.finished();
      }
      after = candidates.getLast();
      List<GroupCandidate> groups =
          candidates.stream()
              .map(this::loadNormalizedGroupOnce)
              .filter(java.util.Objects::nonNull)
              .toList();
      return new Page(candidates.size(), groups);
    }

    private GroupCandidate loadNormalizedGroupOnce(String candidate) {
      String normalized = canonicalizer.canonicalize(URI.create(candidate)).toString();
      if (!processed.add(normalized)) {
        return null;
      }
      ContentCanonicalizationGroup loaded = repository.loadGroup(normalized);
      List<ContentCanonicalizationCandidate> matchingMembers =
          loaded.members().stream()
              .filter(
                  member ->
                      canonicalizer
                          .canonicalize(URI.create(member.canonicalUrl()))
                          .toString()
                          .equals(normalized))
              .toList();
      return new GroupCandidate(
          normalized,
          new ContentCanonicalizationGroup(
              normalized, matchingMembers, loaded.exportHistoryRows()));
    }
  }

  record Page(int scannedRows, List<GroupCandidate> groups) {
    static Page finished() {
      return new Page(0, List.of());
    }

    boolean isFinished() {
      return scannedRows == 0;
    }
  }

  record GroupCandidate(String normalizedUrl, ContentCanonicalizationGroup group) {}
}
