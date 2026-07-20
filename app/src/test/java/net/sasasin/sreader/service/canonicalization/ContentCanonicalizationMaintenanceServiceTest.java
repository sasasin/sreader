package net.sasasin.sreader.service.canonicalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.DatabaseSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.FileSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.GroupSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.ScanSummary;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;
import net.sasasin.sreader.service.article.ArticleUrlCanonicalizerFixtures;
import net.sasasin.sreader.service.text.ContentTextFileStore;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationMaintenanceServiceTest {

  private final ContentCanonicalizationMaintenanceRepository repository = mock();
  private final ContentTextFileStore fileStore = mock();
  private final ContentCanonicalizationMaintenanceService service =
      new ContentCanonicalizationMaintenanceService(
          new ContentCanonicalizationCandidateScanner(
              ArticleUrlCanonicalizerFixtures.configuredFor("canonicalization.test", "/n/"),
              repository),
          new ContentCanonicalizationPlanner(),
          new ContentCanonicalizationExecutor(repository),
          new ContentCanonicalizationFileCleaner(fileStore));

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

    assertThat(result.groups()).isEqualTo(new GroupSummary(0, 1, 0, 0));
    assertThat(result.database()).isEqualTo(DatabaseSummary.empty());
    assertThat(result.files()).isEqualTo(FileSummary.empty());
    assertThat(result.groups().failedGroups()).isZero();
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

    assertThat(result.scan()).isEqualTo(new ScanSummary(1, 0));
    assertThat(result.groups()).isEqualTo(new GroupSummary(1, 0, 0, 0));
    assertThat(result.database()).isEqualTo(new DatabaseSummary(1, 0, 0));
    assertThat(result.files()).isEqualTo(new FileSummary(0, 1, 0));
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

    assertThat(result.groups().renameGroups()).isEqualTo(1);
    assertThat(result.groups().failedGroups()).isEqualTo(1);
    assertThat(result.database()).isEqualTo(DatabaseSummary.empty());
    assertThat(result.files()).isEqualTo(FileSummary.empty());
    assertThat(result.hasFailures()).isTrue();
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

    assertThat(result.scan()).isEqualTo(new ScanSummary(2, 1));
    assertThat(result.groups().processedGroups()).isZero();
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

    assertThat(result.database()).isEqualTo(new DatabaseSummary(1, 1, 0));
    assertThat(result.files()).isEqualTo(new FileSummary(1, 0, 1));
    assertThat(result.groups().failedGroups()).isZero();
    assertThat(result.hasFailures()).isTrue();
  }

  @Test
  void bulkPageScanCountsEveryCandidateRow() {
    String canonical = "https://canonicalization.test/n/article-001";
    ContentCanonicalizationGroup group =
        new ContentCanonicalizationGroup(canonical, List.of(member("a", canonical)), 0);
    when(repository.findCandidateCanonicalUrls(null, null, 10))
        .thenReturn(List.of(canonical, canonical, canonical));
    when(repository.loadGroup(canonical)).thenReturn(group);

    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(null, 10, null, false));

    assertThat(result.scan().scannedRows()).isEqualTo(3);
    assertThat(result.scan().unchangedRows()).isEqualTo(1);
  }

  @Test
  void limitStopsAfterProcessedChangedGroups() {
    String firstCanonical = "https://canonicalization.test/n/article-001";
    String secondCanonical = "https://canonicalization.test/n/article-002";
    ContentCanonicalizationGroup first =
        new ContentCanonicalizationGroup(
            firstCanonical, List.of(member("a", firstCanonical + "?gs=one")), 0);
    ContentCanonicalizationGroup second =
        new ContentCanonicalizationGroup(
            secondCanonical, List.of(member("b", secondCanonical + "?gs=two")), 0);
    when(repository.findCandidateCanonicalUrls(null, null, 100))
        .thenReturn(List.of(firstCanonical + "?gs=one", secondCanonical + "?gs=two"));
    when(repository.loadGroup(firstCanonical)).thenReturn(first);
    when(repository.loadGroup(secondCanonical)).thenReturn(second);
    when(repository.merge(any()))
        .thenReturn(new ContentCanonicalizationMaintenanceRepository.MergeCounts(1, 0, 0));
    when(fileStore.deleteForHeaderId(any()))
        .thenReturn(ContentTextFileStore.DeleteResult.missing());

    ContentCanonicalizationResult result =
        service.canonicalize(
            new ContentCanonicalizationMaintenanceService.Options(null, 100, 1, true));

    assertThat(result.groups().processedGroups()).isEqualTo(1);
    assertThat(result.groups().renameGroups() + result.groups().mergeGroups()).isEqualTo(1);
    // Scanner may load both groups for the page; limit only stops processing changed groups.
    verify(repository, times(1)).merge(any());
    verify(fileStore, times(1)).deleteForHeaderId(any());
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

  private ContentCanonicalizationCandidate member(String suffix, String canonicalUrl) {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    ContentCanonicalizationHeader header =
        new ContentCanonicalizationHeader(
            "0000000000000000000000000000000" + suffix,
            "feed",
            canonicalUrl,
            canonicalUrl,
            canonicalUrl,
            "title",
            now,
            "feed text",
            now,
            now);
    return new ContentCanonicalizationCandidate(
        header,
        Optional.of(new ContentCanonicalizationFullText("full" + suffix, "body", now, now)));
  }
}
