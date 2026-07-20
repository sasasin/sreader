package net.sasasin.sreader.service.canonicalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentCanonicalizationCandidate;
import net.sasasin.sreader.domain.ContentCanonicalizationFullText;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationHeader;
import net.sasasin.sreader.service.article.HashIds;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationPlannerTest {

  private final ContentCanonicalizationPlanner planner = new ContentCanonicalizationPlanner();

  @Test
  void needsChangeWhenMultipleMembersOrNonNormalizedCanonicalUrl() {
    String canonical = "https://example.test/article";
    assertThat(
            planner.needsChange(
                group(
                    canonical,
                    candidate(
                        "a",
                        canonical + "?x",
                        "title",
                        "text",
                        "body",
                        "2026-01-01T00:00Z",
                        "2026-01-01T00:00Z"),
                    candidate(
                        "b",
                        canonical + "?y",
                        "title",
                        "text",
                        "body",
                        "2026-01-02T00:00Z",
                        "2026-01-02T00:00Z"))))
        .isTrue();
    assertThat(
            planner.needsChange(
                group(
                    canonical,
                    candidate(
                        "a",
                        canonical + "?x",
                        "title",
                        "text",
                        null,
                        "2026-01-01T00:00Z",
                        "2026-01-01T00:00Z"))))
        .isTrue();
    assertThat(
            planner.needsChange(
                group(
                    canonical,
                    candidate(
                        "a",
                        canonical,
                        "title",
                        "text",
                        null,
                        "2026-01-01T00:00Z",
                        "2026-01-01T00:00Z"))))
        .isFalse();
  }

  @Test
  void selectsBaseSourceFetchTitleFeedTextPublishedAndSurvivorId() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationCandidate older =
        candidate(
            "a",
            canonical + "?old",
            "old title",
            "short",
            "body",
            "2026-01-01T00:00Z",
            "2026-01-02T00:00Z");
    ContentCanonicalizationCandidate newer =
        candidate(
            "b",
            canonical + "?new",
            "new title",
            "longer text",
            "longer body",
            "2026-01-02T00:00Z",
            "2026-01-03T00:00Z");
    ContentCanonicalizationCandidate mid =
        candidate("c", canonical, "  ", null, null, "2026-01-01T12:00Z", "2026-01-02T12:00Z");

    var plan = planner.plan(group(canonical, newer, older, mid));

    assertThat(plan.survivor().sourceUrl()).isEqualTo(older.sourceUrl());
    assertThat(plan.survivor().fetchUrl()).isEqualTo(newer.fetchUrl());
    assertThat(plan.survivor().title()).isEqualTo("new title");
    assertThat(plan.survivor().feedText()).isEqualTo("longer text");
    assertThat(plan.survivor().publishedAt()).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00Z"));
    assertThat(plan.survivor().createdAt()).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00Z"));
    assertThat(plan.survivor().feedUrlId()).isEqualTo(older.feedUrlId());
    assertThat(plan.selectedFullText()).isPresent();
    assertThat(plan.selectedFullText().orElseThrow().text()).isEqualTo("longer body");
    assertThat(plan.survivorId()).isEqualTo(HashIds.md5(canonical));
    assertThat(plan.feedConflict()).isFalse();
  }

  @Test
  void baseMemberTieBreakUsesCreatedAtThenId() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationCandidate first =
        candidate("b", canonical, "t", "f", null, "2026-01-01T00:00Z", "2026-01-02T00:00Z");
    ContentCanonicalizationCandidate second =
        candidate("a", canonical, "t", "f", null, "2026-01-01T00:00Z", "2026-01-03T00:00Z");
    var plan = planner.plan(group(canonical, first, second));
    assertThat(plan.survivor().feedUrlId()).isEqualTo(second.feedUrlId());
  }

  @Test
  void titleFallsBackToBaseWhenAllBlankAndFeedTextUsesLengthUpdatedAtId() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationCandidate base =
        candidate("a", canonical, null, null, null, "2026-01-01T00:00Z", "2026-01-01T00:00Z");
    ContentCanonicalizationCandidate shortText =
        candidate("b", canonical, " ", "aa", null, "2026-01-02T00:00Z", "2026-01-04T00:00Z");
    ContentCanonicalizationCandidate sameLenOlder =
        candidate("c", canonical, "", "bb", null, "2026-01-03T00:00Z", "2026-01-03T00:00Z");
    ContentCanonicalizationCandidate sameLenNewer =
        candidate("d", canonical, "title-d", "cc", null, "2026-01-04T00:00Z", "2026-01-05T00:00Z");
    var plan = planner.plan(group(canonical, shortText, sameLenOlder, sameLenNewer, base));
    assertThat(plan.survivor().title()).isEqualTo("title-d");
    assertThat(plan.survivor().feedText()).isEqualTo("cc");
  }

  @Test
  void publishedAtIsEarliestNonNullOrNullWhenAllAbsent() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationCandidate withNull =
        new ContentCanonicalizationCandidate(
            new ContentCanonicalizationHeader(
                "a",
                "feed-a",
                canonical,
                canonical,
                canonical,
                "t",
                null,
                "f",
                OffsetDateTime.parse("2026-01-01T00:00Z"),
                OffsetDateTime.parse("2026-01-01T00:00Z")),
            Optional.empty());
    assertThat(planner.plan(group(canonical, withNull)).survivor().publishedAt()).isNull();

    ContentCanonicalizationCandidate later =
        candidate("b", canonical, "t", "f", null, "2026-02-01T00:00Z", "2026-02-01T00:00Z");
    ContentCanonicalizationCandidate earlier =
        candidate("c", canonical, "t", "f", null, "2026-01-15T00:00Z", "2026-01-15T00:00Z");
    assertThat(planner.plan(group(canonical, later, earlier)).survivor().publishedAt())
        .isEqualTo(OffsetDateTime.parse("2026-01-15T00:00Z"));
  }

  @Test
  void detectsFeedConflictAcrossDistinctFeedUrlIds() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationCandidate one =
        candidateWithFeed(
            "a", "feed-1", canonical, "t", "f", null, "2026-01-01T00:00Z", "2026-01-01T00:00Z");
    ContentCanonicalizationCandidate two =
        candidateWithFeed(
            "b", "feed-2", canonical, "t", "f", null, "2026-01-02T00:00Z", "2026-01-02T00:00Z");
    assertThat(planner.plan(group(canonical, one, two)).feedConflict()).isTrue();
    assertThat(
            planner
                .plan(
                    group(
                        canonical,
                        candidate(
                            "a",
                            canonical,
                            "t",
                            "f",
                            null,
                            "2026-01-01T00:00Z",
                            "2026-01-01T00:00Z"),
                        candidate(
                            "b",
                            canonical,
                            "t",
                            "f",
                            null,
                            "2026-01-02T00:00Z",
                            "2026-01-02T00:00Z")))
                .feedConflict())
        .isFalse();
  }

  @Test
  void fullTextSelectionIgnoresBlankAndAppliesLengthThenExtractedAtThenCreatedAtThenId() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationCandidate blankOnly =
        withFullText(
            "a",
            canonical,
            "   ",
            "ft-blank",
            "2026-01-01T00:00Z",
            "2026-01-01T00:00Z",
            "2026-01-01T00:00Z",
            "2026-01-01T00:00Z");
    assertThat(planner.plan(group(canonical, blankOnly)).selectedFullText()).isEmpty();

    ContentCanonicalizationCandidate shortFt =
        withFullText(
            "b",
            canonical,
            "aa",
            "ft-b",
            "2026-01-01T00:00Z",
            "2026-01-05T00:00Z",
            "2026-01-10T00:00Z",
            "2026-01-01T00:00Z");
    ContentCanonicalizationCandidate longEarlyExtract =
        withFullText(
            "c",
            canonical,
            "bbb",
            "ft-c",
            "2026-01-02T00:00Z",
            "2026-01-02T00:00Z",
            "2026-01-05T00:00Z",
            "2026-01-09T00:00Z");
    ContentCanonicalizationCandidate longLateExtractEarlyCreated =
        withFullText(
            "d",
            canonical,
            "bbb",
            "ft-d",
            "2026-01-03T00:00Z",
            "2026-01-03T00:00Z",
            "2026-01-08T00:00Z",
            "2026-01-02T00:00Z");
    ContentCanonicalizationCandidate longLateExtractLateCreatedSmallId =
        withFullText(
            "e",
            canonical,
            "bbb",
            "ft-e",
            "2026-01-04T00:00Z",
            "2026-01-04T00:00Z",
            "2026-01-08T00:00Z",
            "2026-01-08T00:00Z");
    ContentCanonicalizationCandidate longLateExtractLateCreatedLargeId =
        withFullText(
            "f",
            canonical,
            "bbb",
            "ft-z",
            "2026-01-05T00:00Z",
            "2026-01-05T00:00Z",
            "2026-01-08T00:00Z",
            "2026-01-08T00:00Z");

    var plan =
        planner.plan(
            group(
                canonical,
                blankOnly,
                shortFt,
                longEarlyExtract,
                longLateExtractEarlyCreated,
                longLateExtractLateCreatedSmallId,
                longLateExtractLateCreatedLargeId));
    // max length, then max extractedAt, then min createdAt (reverseOrder on createdAt), then max id
    assertThat(plan.selectedFullText().orElseThrow().id()).isEqualTo("ft-d");
  }

  @Test
  void fullTextSelectionUsesLargestIdWhenAllEarlierTieBreakersAreEqual() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationCandidate smallerId =
        withFullText(
            "a",
            canonical,
            "same length",
            "ft-a",
            "2026-01-01T00:00Z",
            "2026-01-01T00:00Z",
            "2026-01-02T00:00Z",
            "2026-01-03T00:00Z");
    ContentCanonicalizationCandidate largerId =
        withFullText(
            "b",
            canonical,
            "same length",
            "ft-z",
            "2026-01-04T00:00Z",
            "2026-01-04T00:00Z",
            "2026-01-02T00:00Z",
            "2026-01-03T00:00Z");

    assertThat(planner.plan(group(canonical, smallerId, largerId)).selectedFullText())
        .map(ContentCanonicalizationFullText::id)
        .contains("ft-z");
  }

  private ContentCanonicalizationGroup group(
      String canonical, ContentCanonicalizationCandidate... members) {
    return new ContentCanonicalizationGroup(canonical, List.of(members), 0);
  }

  private ContentCanonicalizationCandidate candidate(
      String id,
      String url,
      String title,
      String feedText,
      String fullText,
      String created,
      String updated) {
    return candidateWithFeed(id, "feed", url, title, feedText, fullText, created, updated);
  }

  private ContentCanonicalizationCandidate candidateWithFeed(
      String id,
      String feedUrlId,
      String url,
      String title,
      String feedText,
      String fullText,
      String created,
      String updated) {
    OffsetDateTime createdAt = OffsetDateTime.parse(created);
    OffsetDateTime updatedAt = OffsetDateTime.parse(updated);
    ContentCanonicalizationHeader header =
        new ContentCanonicalizationHeader(
            id, feedUrlId, url, url, url, title, createdAt, feedText, createdAt, updatedAt);
    Optional<ContentCanonicalizationFullText> text =
        fullText == null
            ? Optional.empty()
            : Optional.of(
                new ContentCanonicalizationFullText("ft-" + id, fullText, updatedAt, createdAt));
    return new ContentCanonicalizationCandidate(header, text);
  }

  private ContentCanonicalizationCandidate withFullText(
      String id,
      String url,
      String text,
      String fullTextId,
      String created,
      String updated,
      String extractedAt,
      String fullTextCreatedAt) {
    OffsetDateTime createdAt = OffsetDateTime.parse(created);
    OffsetDateTime updatedAt = OffsetDateTime.parse(updated);
    ContentCanonicalizationHeader header =
        new ContentCanonicalizationHeader(
            id, "feed-" + id, url, url, url, "title", createdAt, "feed", createdAt, updatedAt);
    return new ContentCanonicalizationCandidate(
        header,
        Optional.of(
            new ContentCanonicalizationFullText(
                fullTextId,
                text,
                OffsetDateTime.parse(extractedAt),
                OffsetDateTime.parse(fullTextCreatedAt))));
  }
}
