package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationDomainTest {

  private final OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");

  @Test
  void candidateRejectsNullHeaderAndNullOptional() {
    ContentCanonicalizationHeader header = header("a");
    assertThatThrownBy(() -> new ContentCanonicalizationCandidate(null, Optional.empty()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("header");
    assertThatThrownBy(() -> new ContentCanonicalizationCandidate(header, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fullText");
  }

  @Test
  void candidateRepresentsFullTextPresenceAndAbsence() {
    ContentCanonicalizationHeader header = header("a");
    ContentCanonicalizationCandidate without =
        new ContentCanonicalizationCandidate(header, Optional.empty());
    assertThat(without.fullText()).isEmpty();
    assertThat(without.id()).isEqualTo("a");

    ContentCanonicalizationFullText text =
        new ContentCanonicalizationFullText("ft", "body", now, now);
    ContentCanonicalizationCandidate with =
        new ContentCanonicalizationCandidate(header, Optional.of(text));
    assertThat(with.fullText()).contains(text);
  }

  @Test
  void groupRejectsNullListNullMembersAndDefensivelyCopies() {
    assertThatThrownBy(() -> new ContentCanonicalizationGroup("https://example.test/a", null, 0))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("members");

    List<ContentCanonicalizationCandidate> members = new ArrayList<>();
    members.add(new ContentCanonicalizationCandidate(header("a"), Optional.empty()));
    members.add(null);
    assertThatThrownBy(() -> new ContentCanonicalizationGroup("https://example.test/a", members, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");

    List<ContentCanonicalizationCandidate> mutable = new ArrayList<>();
    ContentCanonicalizationCandidate first =
        new ContentCanonicalizationCandidate(header("a"), Optional.empty());
    mutable.add(first);
    ContentCanonicalizationGroup group =
        new ContentCanonicalizationGroup("https://example.test/a", mutable, 1);
    mutable.add(new ContentCanonicalizationCandidate(header("b"), Optional.empty()));
    assertThat(group.members()).containsExactly(first);
  }

  @Test
  void planSelectedFullTextIsOptionalAndSurvivorHasNoFullTextFields() {
    ContentCanonicalizationGroup group =
        new ContentCanonicalizationGroup(
            "https://example.test/a",
            List.of(new ContentCanonicalizationCandidate(header("a"), Optional.empty())),
            0);
    ContentCanonicalizationSurvivor survivor =
        new ContentCanonicalizationSurvivor(
            "survivor",
            "feed",
            "https://example.test/a",
            "https://example.test/a",
            "https://example.test/a",
            "title",
            null,
            "text",
            now);
    ContentCanonicalizationPlan plan =
        new ContentCanonicalizationPlan(group, survivor, Optional.empty(), false);
    assertThat(plan.selectedFullText()).isEmpty();
    assertThat(plan.survivorId()).isEqualTo("survivor");
    assertThat(plan.survivor().getClass().getRecordComponents())
        .extracting(c -> c.getName())
        .doesNotContain("fullText", "fullTextId", "extractedAt", "fullTextCreatedAt");
  }

  private ContentCanonicalizationHeader header(String id) {
    return new ContentCanonicalizationHeader(
        id,
        "feed",
        "https://example.test/" + id,
        "https://example.test/" + id,
        "https://example.test/" + id,
        "title",
        now,
        "text",
        now,
        now);
  }
}
