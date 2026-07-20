package net.sasasin.sreader.service.canonicalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentCanonicalizationCandidate;
import net.sasasin.sreader.domain.ContentCanonicalizationFullText;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationHeader;
import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.FileSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationSurvivor;
import net.sasasin.sreader.service.text.ContentTextFileStore;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationFileCleanerTest {

  private final OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private final ContentTextFileStore fileStore = mock();
  private final ContentCanonicalizationFileCleaner cleaner =
      new ContentCanonicalizationFileCleaner(fileStore);

  @Test
  void countsDeletedFile() {
    ContentCanonicalizationPlan plan = plan(List.of("a"));
    when(fileStore.deleteForHeaderId(id("a")))
        .thenReturn(ContentTextFileStore.DeleteResult.deleted());

    assertThat(cleaner.clean(plan)).isEqualTo(new FileSummary(1, 0, 0));
  }

  @Test
  void countsMissingFile() {
    ContentCanonicalizationPlan plan = plan(List.of("a"));
    when(fileStore.deleteForHeaderId(id("a")))
        .thenReturn(ContentTextFileStore.DeleteResult.missing());

    assertThat(cleaner.clean(plan)).isEqualTo(new FileSummary(0, 1, 0));
  }

  @Test
  void countsFailedFileAndContinuesWithRemainingMembers() {
    ContentCanonicalizationPlan plan = plan(List.of("a", "b"));
    when(fileStore.deleteForHeaderId(id("a")))
        .thenReturn(ContentTextFileStore.DeleteResult.failed("denied"));
    when(fileStore.deleteForHeaderId(id("b")))
        .thenReturn(ContentTextFileStore.DeleteResult.deleted());

    assertThat(cleaner.clean(plan)).isEqualTo(new FileSummary(1, 0, 1));
    verify(fileStore).deleteForHeaderId(id("a"));
    verify(fileStore).deleteForHeaderId(id("b"));
  }

  @Test
  void countsMixedOutcomes() {
    ContentCanonicalizationPlan plan = plan(List.of("a", "b", "c"));
    when(fileStore.deleteForHeaderId(id("a")))
        .thenReturn(ContentTextFileStore.DeleteResult.deleted());
    when(fileStore.deleteForHeaderId(id("b")))
        .thenReturn(ContentTextFileStore.DeleteResult.missing());
    when(fileStore.deleteForHeaderId(id("c")))
        .thenReturn(ContentTextFileStore.DeleteResult.failed("io"));

    assertThat(cleaner.clean(plan)).isEqualTo(new FileSummary(1, 1, 1));
  }

  @Test
  void invokesDeleteOncePerMemberId() {
    ContentCanonicalizationPlan plan = plan(List.of("a", "b"));
    when(fileStore.deleteForHeaderId(id("a")))
        .thenReturn(ContentTextFileStore.DeleteResult.missing());
    when(fileStore.deleteForHeaderId(id("b")))
        .thenReturn(ContentTextFileStore.DeleteResult.missing());

    cleaner.clean(plan);

    verify(fileStore, times(1)).deleteForHeaderId(id("a"));
    verify(fileStore, times(1)).deleteForHeaderId(id("b"));
  }

  private ContentCanonicalizationPlan plan(List<String> suffixes) {
    List<ContentCanonicalizationCandidate> members =
        suffixes.stream().map(this::candidate).toList();
    String canonical = "https://example.test/article";
    ContentCanonicalizationGroup group = new ContentCanonicalizationGroup(canonical, members, 0);
    ContentCanonicalizationHeader header = members.getFirst().header();
    ContentCanonicalizationSurvivor survivor =
        new ContentCanonicalizationSurvivor(
            header.id(),
            header.feedUrlId(),
            header.sourceUrl(),
            header.fetchUrl(),
            canonical,
            header.title(),
            header.publishedAt(),
            header.feedText(),
            header.createdAt());
    return new ContentCanonicalizationPlan(group, survivor, Optional.empty(), false);
  }

  private ContentCanonicalizationCandidate candidate(String suffix) {
    ContentCanonicalizationHeader header =
        new ContentCanonicalizationHeader(
            id(suffix),
            "feed",
            "https://example.test/" + suffix,
            "https://example.test/" + suffix,
            "https://example.test/article",
            "title",
            now,
            "feed text",
            now,
            now);
    return new ContentCanonicalizationCandidate(
        header,
        Optional.of(new ContentCanonicalizationFullText("full" + suffix, "body", now, now)));
  }

  private static String id(String suffix) {
    return "0000000000000000000000000000000" + suffix;
  }
}
