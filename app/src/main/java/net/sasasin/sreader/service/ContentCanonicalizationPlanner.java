package net.sasasin.sreader.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationMember;
import net.sasasin.sreader.domain.ContentCanonicalizationPlan;

/** Pure canonicalization business rules, including all survivor selection tie-breaks. */
final class ContentCanonicalizationPlanner {
  boolean needsChange(ContentCanonicalizationGroup group) {
    return group.members().size() > 1
        || group.members().stream().anyMatch(m -> !m.canonicalUrl().equals(group.canonicalUrl()));
  }

  ContentCanonicalizationPlan plan(ContentCanonicalizationGroup group) {
    List<ContentCanonicalizationMember> members = group.members();
    ContentCanonicalizationMember base = selectBaseMember(members);
    ContentCanonicalizationMember fetch = selectFetchMember(members);
    ContentCanonicalizationMember title = selectTitleMember(members, base);
    ContentCanonicalizationMember feedText = selectFeedTextMember(members, base);
    ContentCanonicalizationMember values =
        new ContentCanonicalizationMember(
            base.id(),
            base.feedUrlId(),
            selectSourceMember(members, group.canonicalUrl()).sourceUrl(),
            fetch.fetchUrl(),
            group.canonicalUrl(),
            title.title(),
            selectPublishedAt(members),
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
    return new ContentCanonicalizationPlan(
        group,
        values,
        selectFullTextMember(members),
        HashIds.md5(group.canonicalUrl()),
        detectFeedConflict(members));
  }

  ContentCanonicalizationMember selectSourceMember(
      List<ContentCanonicalizationMember> members, String canonicalUrl) {
    return members.stream()
        .filter(m -> !m.sourceUrl().equals(canonicalUrl))
        .min(oldest())
        .orElseGet(() -> members.stream().min(oldest()).orElseThrow());
  }

  ContentCanonicalizationMember selectFetchMember(List<ContentCanonicalizationMember> members) {
    return members.stream()
        .max(
            Comparator.comparing(ContentCanonicalizationMember::updatedAt)
                .thenComparing(ContentCanonicalizationMember::createdAt)
                .thenComparing(ContentCanonicalizationMember::id))
        .orElseThrow();
  }

  ContentCanonicalizationMember selectBaseMember(List<ContentCanonicalizationMember> members) {
    return members.stream().min(oldest()).orElseThrow();
  }

  ContentCanonicalizationMember selectTitleMember(
      List<ContentCanonicalizationMember> members, ContentCanonicalizationMember base) {
    return members.stream()
        .filter(m -> !blank(m.title()))
        .max(
            Comparator.comparing(ContentCanonicalizationMember::updatedAt)
                .thenComparing(ContentCanonicalizationMember::id))
        .orElse(base);
  }

  ContentCanonicalizationMember selectFeedTextMember(
      List<ContentCanonicalizationMember> members, ContentCanonicalizationMember base) {
    return members.stream()
        .filter(m -> !blank(m.feedText()))
        .max(
            Comparator.comparingInt((ContentCanonicalizationMember m) -> m.feedText().length())
                .thenComparing(ContentCanonicalizationMember::updatedAt)
                .thenComparing(ContentCanonicalizationMember::id))
        .orElse(base);
  }

  OffsetDateTime selectPublishedAt(List<ContentCanonicalizationMember> members) {
    return members.stream()
        .map(ContentCanonicalizationMember::publishedAt)
        .filter(Objects::nonNull)
        .min(OffsetDateTime::compareTo)
        .orElse(null);
  }

  ContentCanonicalizationMember selectFullTextMember(List<ContentCanonicalizationMember> members) {
    return members.stream()
        .filter(m -> !blank(m.fullText()))
        .max(
            Comparator.comparingInt((ContentCanonicalizationMember m) -> m.fullText().length())
                .thenComparing(ContentCanonicalizationMember::extractedAt)
                .thenComparing(
                    ContentCanonicalizationMember::fullTextCreatedAt, Comparator.reverseOrder())
                .thenComparing(ContentCanonicalizationMember::fullTextId))
        .orElse(null);
  }

  boolean detectFeedConflict(List<ContentCanonicalizationMember> members) {
    return members.stream().map(ContentCanonicalizationMember::feedUrlId).distinct().count() > 1;
  }

  private Comparator<ContentCanonicalizationMember> oldest() {
    return Comparator.comparing(ContentCanonicalizationMember::createdAt)
        .thenComparing(ContentCanonicalizationMember::id);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
