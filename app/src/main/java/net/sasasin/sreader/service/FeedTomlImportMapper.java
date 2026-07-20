package net.sasasin.sreader.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;

/**
 * Maps schema-validated raw feed tables into domain {@link FeedTomlService.ImportFeed} values. Does
 * not parse TOML text, inspect quotes/escapes, or touch repositories.
 */
final class FeedTomlImportMapper {

  record Result(List<FeedTomlService.ImportFeed> feeds, List<FeedTomlIssue> issues) {
    Result {
      feeds = List.copyOf(Objects.requireNonNull(feeds, "feeds"));
      issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }
  }

  Result map(OptionalInt schemaVersion, List<FeedTomlEntry> entries) {
    Objects.requireNonNull(entries, "entries must not be null");
    List<FeedTomlIssue> issues = new ArrayList<>();
    if (schemaVersion.isPresent()) {
      int version = schemaVersion.getAsInt();
      if (version != 1 && version != 2) {
        issues.add(
            FeedTomlIssue.domain(
                FeedTomlPosition.unknown(),
                "schema_version",
                "unsupported schema_version: " + version,
                issues.size()));
      }
    }

    Set<String> seenUrls = new HashSet<>();
    List<FeedTomlService.ImportFeed> feeds = new ArrayList<>();
    for (FeedTomlEntry entry : entries) {
      mapEntry(entry, seenUrls, feeds, issues);
    }
    return new Result(feeds, issues);
  }

  private void mapEntry(
      FeedTomlEntry entry,
      Set<String> seenUrls,
      List<FeedTomlService.ImportFeed> feeds,
      List<FeedTomlIssue> issues) {
    int issuesBefore = issues.size();
    String url = mapUrl(entry, seenUrls, issues);

    FeedStatus status = FeedStatus.ACTIVE;
    boolean statusOk = true;
    if (entry.status().isPresent()) {
      String statusValue = entry.status().get();
      try {
        status = FeedStatus.fromValue(statusValue);
      } catch (IllegalArgumentException e) {
        issues.add(
            FeedTomlIssue.domain(
                entry.fieldPosition("status"),
                entry.path("status"),
                entry.path("status") + " must be active or unsubscribed: " + statusValue,
                issues.size()));
        statusOk = false;
      }
    }

    UnsubscribeReason reason = null;
    boolean reasonOk = true;
    if (status == FeedStatus.UNSUBSCRIBED && entry.unsubscribeReason().isEmpty()) {
      reason = UnsubscribeReason.OTHER;
    } else if (entry.unsubscribeReason().isPresent()) {
      String reasonValue = entry.unsubscribeReason().get();
      try {
        reason = UnsubscribeReason.fromValue(reasonValue);
      } catch (IllegalArgumentException e) {
        issues.add(
            FeedTomlIssue.domain(
                entry.fieldPosition("unsubscribe_reason"),
                entry.path("unsubscribe_reason"),
                entry.path("unsubscribe_reason") + " is invalid: " + reasonValue,
                issues.size()));
        reasonOk = false;
      }
    }

    OffsetDateTime unsubscribedAt = mapUnsubscribedAt(entry, issues);
    String note = entry.note().orElse(null);
    FullTextMethod method = mapFullTextMethod(entry, issues);

    if (status == FeedStatus.ACTIVE) {
      // Active feeds discard unsubscribe metadata at the import boundary (existing external
      // behavior). Invalid unsubscribe metadata is still validated above for active tables.
      reason = null;
      unsubscribedAt = null;
      note = null;
    }

    if (url != null && statusOk && reasonOk && issues.size() == issuesBefore) {
      feeds.add(
          new FeedTomlService.ImportFeed(
              entry.displayIndex(), url, status, reason, unsubscribedAt, note, method));
    }
  }

  private String mapUrl(FeedTomlEntry entry, Set<String> seenUrls, List<FeedTomlIssue> issues) {
    if (entry.url().isEmpty()) {
      // Missing/non-string URL already reported as schema issue.
      return null;
    }
    String raw = entry.url().get();
    try {
      String url = FeedUrlNormalizer.normalizeStrict(raw);
      if (!seenUrls.add(url)) {
        issues.add(
            FeedTomlIssue.domain(
                entry.urlPosition(),
                entry.path("url"),
                entry.path("url") + " duplicates another feed after normalization: " + url,
                issues.size()));
      }
      return url;
    } catch (IllegalArgumentException e) {
      issues.add(
          FeedTomlIssue.domain(
              entry.urlPosition(),
              entry.path("url"),
              entry.path("url") + ": " + e.getMessage(),
              issues.size()));
      return null;
    }
  }

  private OffsetDateTime mapUnsubscribedAt(FeedTomlEntry entry, List<FeedTomlIssue> issues) {
    if (entry.unsubscribedAt().isEmpty()) {
      return null;
    }
    String value = entry.unsubscribedAt().get();
    try {
      return OffsetDateTime.parse(value);
    } catch (RuntimeException e) {
      issues.add(
          FeedTomlIssue.domain(
              entry.fieldPosition("unsubscribed_at"),
              entry.path("unsubscribed_at"),
              entry.path("unsubscribed_at") + " must be an offset date-time: " + value,
              issues.size()));
      return null;
    }
  }

  private FullTextMethod mapFullTextMethod(FeedTomlEntry entry, List<FeedTomlIssue> issues) {
    if (entry.fullTextMethod().isEmpty()) {
      return FullTextMethod.HTTP;
    }
    String value = entry.fullTextMethod().get();
    try {
      return FullTextMethod.fromValue(value);
    } catch (IllegalArgumentException e) {
      issues.add(
          FeedTomlIssue.domain(
              entry.fieldPosition("full_text_method"),
              entry.path("full_text_method"),
              entry.path("full_text_method") + " is invalid: " + value,
              issues.size()));
      return FullTextMethod.HTTP;
    }
  }
}
