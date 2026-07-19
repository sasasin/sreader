package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationMember;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationPlannerTest {

  private final ContentCanonicalizationPlanner planner = new ContentCanonicalizationPlanner();

  @Test
  void selectsSurvivorValuesUsingDocumentedTieBreaks() {
    String canonical = "https://example.test/article";
    ContentCanonicalizationMember older =
        member(
            "a",
            canonical + "?old",
            "old title",
            "short",
            "body",
            "2026-01-01T00:00Z",
            "2026-01-02T00:00Z");
    ContentCanonicalizationMember newer =
        member(
            "b",
            canonical + "?new",
            "new title",
            "longer text",
            "longer body",
            "2026-01-02T00:00Z",
            "2026-01-03T00:00Z");

    var plan = planner.plan(new ContentCanonicalizationGroup(canonical, List.of(newer, older), 0));

    assertThat(plan.survivorValues().sourceUrl()).isEqualTo(older.sourceUrl());
    assertThat(plan.survivorValues().fetchUrl()).isEqualTo(newer.fetchUrl());
    assertThat(plan.survivorValues().title()).isEqualTo("new title");
    assertThat(plan.survivorValues().feedText()).isEqualTo("longer text");
    assertThat(plan.selectedFullText().id()).isEqualTo(newer.id());
    assertThat(plan.survivorId()).isEqualTo(HashIds.md5(canonical));
  }

  @Test
  void recognizesUnchangedSingleCanonicalMember() {
    String canonical = "https://example.test/article";
    assertThat(
            planner.needsChange(
                new ContentCanonicalizationGroup(
                    canonical,
                    List.of(
                        member(
                            "a",
                            canonical,
                            "title",
                            "text",
                            null,
                            "2026-01-01T00:00Z",
                            "2026-01-01T00:00Z")),
                    0)))
        .isFalse();
  }

  private ContentCanonicalizationMember member(
      String id,
      String url,
      String title,
      String feedText,
      String fullText,
      String created,
      String updated) {
    OffsetDateTime createdAt = OffsetDateTime.parse(created);
    OffsetDateTime updatedAt = OffsetDateTime.parse(updated);
    return new ContentCanonicalizationMember(
        id,
        "feed-" + id,
        url,
        url,
        url,
        title,
        createdAt,
        feedText,
        createdAt,
        updatedAt,
        fullText,
        fullText == null ? null : fullText,
        updatedAt,
        createdAt);
  }
}
