package net.sasasin.sreader.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentCanonicalizationCandidate;
import net.sasasin.sreader.domain.ContentCanonicalizationFullText;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationSurvivor;

/** Pure canonicalization business rules, including all survivor selection tie-breaks. */
final class ContentCanonicalizationPlanner {
  boolean needsChange(ContentCanonicalizationGroup group) {
    return group.members().size() > 1
        || group.members().stream().anyMatch(m -> !m.canonicalUrl().equals(group.canonicalUrl()));
  }

  ContentCanonicalizationPlan plan(ContentCanonicalizationGroup group) {
    List<ContentCanonicalizationCandidate> members = group.members();
    ContentCanonicalizationCandidate base = selectBaseMember(members);
    ContentCanonicalizationCandidate fetch = selectFetchMember(members);
    ContentCanonicalizationCandidate title = selectTitleMember(members, base);
    ContentCanonicalizationCandidate feedText = selectFeedTextMember(members, base);
    ContentCanonicalizationSurvivor survivor =
        new ContentCanonicalizationSurvivor(
            HashIds.md5(group.canonicalUrl()),
            base.feedUrlId(),
            selectSourceMember(members, group.canonicalUrl()).sourceUrl(),
            fetch.fetchUrl(),
            group.canonicalUrl(),
            title.title(),
            selectPublishedAt(members),
            feedText.feedText(),
            members.stream()
                .map(ContentCanonicalizationCandidate::createdAt)
                .min(OffsetDateTime::compareTo)
                .orElseThrow());
    return new ContentCanonicalizationPlan(
        group, survivor, selectFullText(members), detectFeedConflict(members));
  }

  ContentCanonicalizationCandidate selectSourceMember(
      List<ContentCanonicalizationCandidate> members, String canonicalUrl) {
    return members.stream()
        .filter(m -> !m.sourceUrl().equals(canonicalUrl))
        .min(oldest())
        .orElseGet(() -> members.stream().min(oldest()).orElseThrow());
  }

  ContentCanonicalizationCandidate selectFetchMember(
      List<ContentCanonicalizationCandidate> members) {
    return members.stream()
        .max(
            Comparator.comparing(ContentCanonicalizationCandidate::updatedAt)
                .thenComparing(ContentCanonicalizationCandidate::createdAt)
                .thenComparing(ContentCanonicalizationCandidate::id))
        .orElseThrow();
  }

  ContentCanonicalizationCandidate selectBaseMember(
      List<ContentCanonicalizationCandidate> members) {
    return members.stream().min(oldest()).orElseThrow();
  }

  ContentCanonicalizationCandidate selectTitleMember(
      List<ContentCanonicalizationCandidate> members, ContentCanonicalizationCandidate base) {
    return members.stream()
        .filter(m -> !blank(m.title()))
        .max(
            Comparator.comparing(ContentCanonicalizationCandidate::updatedAt)
                .thenComparing(ContentCanonicalizationCandidate::id))
        .orElse(base);
  }

  ContentCanonicalizationCandidate selectFeedTextMember(
      List<ContentCanonicalizationCandidate> members, ContentCanonicalizationCandidate base) {
    return members.stream()
        .filter(m -> !blank(m.feedText()))
        .max(
            Comparator.comparingInt((ContentCanonicalizationCandidate m) -> m.feedText().length())
                .thenComparing(ContentCanonicalizationCandidate::updatedAt)
                .thenComparing(ContentCanonicalizationCandidate::id))
        .orElse(base);
  }

  OffsetDateTime selectPublishedAt(List<ContentCanonicalizationCandidate> members) {
    return members.stream()
        .map(ContentCanonicalizationCandidate::publishedAt)
        .filter(Objects::nonNull)
        .min(OffsetDateTime::compareTo)
        .orElse(null);
  }

  Optional<ContentCanonicalizationFullText> selectFullText(
      List<ContentCanonicalizationCandidate> members) {
    return members.stream()
        .map(ContentCanonicalizationCandidate::fullText)
        .flatMap(Optional::stream)
        .filter(ft -> !blank(ft.text()))
        .max(
            Comparator.comparingInt((ContentCanonicalizationFullText ft) -> ft.text().length())
                .thenComparing(ContentCanonicalizationFullText::extractedAt)
                .thenComparing(
                    ContentCanonicalizationFullText::createdAt, Comparator.reverseOrder())
                .thenComparing(ContentCanonicalizationFullText::id));
  }

  boolean detectFeedConflict(List<ContentCanonicalizationCandidate> members) {
    return members.stream().map(ContentCanonicalizationCandidate::feedUrlId).distinct().count() > 1;
  }

  private Comparator<ContentCanonicalizationCandidate> oldest() {
    return Comparator.comparing(ContentCanonicalizationCandidate::createdAt)
        .thenComparing(ContentCanonicalizationCandidate::id);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
