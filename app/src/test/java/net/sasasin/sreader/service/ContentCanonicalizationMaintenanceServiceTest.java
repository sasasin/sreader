package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationMember;
import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationMaintenanceServiceTest {

  private final ContentCanonicalizationMaintenanceRepository repository = mock();
  private final ContentTextFileStore fileStore = mock();
  private final ContentCanonicalizationMaintenanceService service =
      new ContentCanonicalizationMaintenanceService(
          new ArticleUrlCanonicalizer("canonicalization.test", "/n/"), repository, fileStore);

  @Test
  void dryRunPlansSyntheticMergeWithoutChangingDatabaseOrFiles() {
    String canonical = "https://canonicalization.test/n/article-001";
    ContentCanonicalizationGroup group =
        new ContentCanonicalizationGroup(
            canonical,
            List.of(member("a", canonical + "?gs=one"), member("b", canonical + "?gs=two")),
            2);
    when(repository.findCandidateCanonicalUrls("canonicalization.test", null, 100))
        .thenReturn(List.of(canonical + "?gs=one"));
    when(repository.loadGroup(canonical)).thenReturn(group);

    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(
                "canonicalization.test", 100, null, false));

    assertThat(result.mergeGroups()).isEqualTo(1);
    assertThat(result.deletedContentHeaders()).isZero();
    assertThat(result.deletedFiles()).isZero();
    verify(repository, never()).merge(any());
    verify(fileStore, never()).deleteForHeaderId(any());
  }

  @Test
  void applyMergesAndDeletesFilesOnlyAfterRepositorySucceeds() {
    String canonical = "https://canonicalization.test/n/article-001";
    ContentCanonicalizationGroup group =
        new ContentCanonicalizationGroup(canonical, List.of(member("a", canonical + "?gs=one")), 0);
    when(repository.findCandidateCanonicalUrls(null, null, 100))
        .thenReturn(List.of(canonical + "?gs=one"));
    when(repository.loadGroup(canonical)).thenReturn(group);
    when(repository.merge(any()))
        .thenReturn(new ContentCanonicalizationMaintenanceRepository.MergeCounts(1, 0, 0));
    when(fileStore.deleteForHeaderId("0000000000000000000000000000000a"))
        .thenReturn(ContentTextFileStore.DeleteResult.missing());

    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(null, 100, 1, true));

    assertThat(result.renameGroups()).isEqualTo(1);
    assertThat(result.deletedContentHeaders()).isEqualTo(1);
    assertThat(result.missingFiles()).isEqualTo(1);
    verify(repository).merge(any());
    verify(fileStore).deleteForHeaderId("0000000000000000000000000000000a");
  }

  @Test
  void repositoryFailureDoesNotDeleteFiles() {
    String canonical = "https://canonicalization.test/n/article-001";
    when(repository.findCandidateCanonicalUrls(null, null, 100))
        .thenReturn(List.of(canonical + "?gs=one"));
    when(repository.loadGroup(canonical))
        .thenReturn(
            new ContentCanonicalizationGroup(
                canonical, List.of(member("a", canonical + "?gs=one")), 0));
    when(repository.merge(any())).thenThrow(new IllegalStateException("rollback"));

    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(null, 100, 1, true));

    assertThat(result.failedGroups()).isEqualTo(1);
    verify(fileStore, never()).deleteForHeaderId(any());
  }

  @Test
  void skipsUnchangedAndDuplicateCandidatesAndNormalizesBlankHost() {
    String canonical = "https://canonicalization.test/n/article-001";
    ContentCanonicalizationGroup group =
        new ContentCanonicalizationGroup(canonical, List.of(member("a", canonical)), 0);
    when(repository.findCandidateCanonicalUrls(null, null, 2))
        .thenReturn(List.of(canonical, canonical));
    when(repository.loadGroup(canonical)).thenReturn(group);

    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(" ", 2, null, false));

    assertThat(result.scannedRows()).isEqualTo(2);
    assertThat(result.unchangedRows()).isEqualTo(1);
    assertThat(result.processedGroups()).isZero();
    verify(repository, never()).merge(any());
  }

  @Test
  void countsEveryFileOutcomeAfterSuccessfulMerge() {
    String canonical = "https://canonicalization.test/n/article-002";
    ContentCanonicalizationGroup group =
        new ContentCanonicalizationGroup(
            canonical,
            List.of(member("a", canonical + "?gs=one"), member("b", canonical + "?gs=two")),
            0);
    when(repository.findCandidateCanonicalUrls(null, null, 100))
        .thenReturn(List.of(canonical + "?gs=one"));
    when(repository.loadGroup(canonical)).thenReturn(group);
    when(repository.merge(any()))
        .thenReturn(new ContentCanonicalizationMaintenanceRepository.MergeCounts(1, 1, 0));
    when(fileStore.deleteForHeaderId("0000000000000000000000000000000a"))
        .thenReturn(ContentTextFileStore.DeleteResult.deleted());
    when(fileStore.deleteForHeaderId("0000000000000000000000000000000b"))
        .thenReturn(ContentTextFileStore.DeleteResult.failed("denied"));

    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(null, 100, 1, true));

    assertThat(result.deletedFiles()).isEqualTo(1);
    assertThat(result.failedFiles()).isEqualTo(1);
    assertThat(result.hasFailures()).isTrue();
  }

  @Test
  void rejectsNonPositiveOptions() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new ContentCanonicalizationMaintenanceService.Options(null, 0, null, false));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new ContentCanonicalizationMaintenanceService.Options(null, 1, 0, false));
  }

  private ContentCanonicalizationMember member(String suffix, String canonicalUrl) {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    return new ContentCanonicalizationMember(
        "0000000000000000000000000000000" + suffix,
        "feed",
        canonicalUrl,
        canonicalUrl,
        canonicalUrl,
        "title",
        now,
        "feed text",
        now,
        now,
        "full" + suffix,
        "body",
        now,
        now);
  }
}
