package net.sasasin.sreader.service.canonicalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentCanonicalizationCandidate;
import net.sasasin.sreader.domain.ContentCanonicalizationFullText;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationHeader;
import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationResult;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.DatabaseSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.FileSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.GroupSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.ScanSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationSurvivor;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository.MergeCounts;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationResultAccumulatorTest {

  private final OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");

  @Test
  void initialSnapshotMatchesEmptyResult() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();

    assertThat(accumulator.snapshot()).isEqualTo(ContentCanonicalizationResult.empty());
    assertThat(accumulator.processedGroups()).isZero();
  }

  @Test
  void addScannedRowsBulkAndZeroAreNoOp() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.addScannedRows(5);
    accumulator.addScannedRows(0);

    assertThat(accumulator.snapshot().scan()).isEqualTo(new ScanSummary(5, 0));
  }

  @Test
  void addUnchangedRowsAccumulatesWithoutAffectingProcessedGroups() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.addUnchangedRows(2);
    accumulator.addUnchangedRows(3);

    assertThat(accumulator.snapshot().scan()).isEqualTo(new ScanSummary(0, 5));
    assertThat(accumulator.processedGroups()).isZero();
  }

  @Test
  void recordPlannedRenameGroup() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.recordPlannedGroup(plan(1, false));

    assertThat(accumulator.snapshot().groups()).isEqualTo(new GroupSummary(1, 0, 0, 0));
    assertThat(accumulator.processedGroups()).isEqualTo(1);

    ContentCanonicalizationResultAccumulator withConflict =
        new ContentCanonicalizationResultAccumulator();
    withConflict.recordPlannedGroup(plan(1, true));
    assertThat(withConflict.snapshot().groups()).isEqualTo(new GroupSummary(1, 0, 1, 0));
  }

  @Test
  void recordPlannedMergeGroup() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.recordPlannedGroup(plan(2, false));

    assertThat(accumulator.snapshot().groups()).isEqualTo(new GroupSummary(0, 1, 0, 0));
    assertThat(accumulator.processedGroups()).isEqualTo(1);

    ContentCanonicalizationResultAccumulator withConflict =
        new ContentCanonicalizationResultAccumulator();
    withConflict.recordPlannedGroup(plan(2, true));
    assertThat(withConflict.snapshot().groups()).isEqualTo(new GroupSummary(0, 1, 1, 0));
  }

  @Test
  void addMergeCountsAccumulatesDatabaseSummary() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.addMergeCounts(new MergeCounts(1, 2, 3));
    accumulator.addMergeCounts(new MergeCounts(4, 5, 6));

    assertThat(accumulator.snapshot().database()).isEqualTo(new DatabaseSummary(5, 7, 9));
  }

  @Test
  void addFileSummaryAccumulates() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.addFileSummary(new FileSummary(1, 2, 0));
    accumulator.addFileSummary(new FileSummary(3, 0, 1));

    assertThat(accumulator.snapshot().files()).isEqualTo(new FileSummary(4, 2, 1));
  }

  @Test
  void recordFailedGroupDoesNotIncreaseProcessedGroups() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.recordPlannedGroup(plan(1, false));
    accumulator.recordFailedGroup();
    accumulator.recordFailedGroup();

    ContentCanonicalizationResult snapshot = accumulator.snapshot();
    assertThat(snapshot.groups()).isEqualTo(new GroupSummary(1, 0, 0, 2));
    assertThat(accumulator.processedGroups()).isEqualTo(1);
    assertThat(snapshot.hasFailures()).isTrue();
  }

  @Test
  void snapshotIsIndependentOfLaterUpdates() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();
    accumulator.addScannedRows(1);
    ContentCanonicalizationResult first = accumulator.snapshot();
    accumulator.addScannedRows(2);
    ContentCanonicalizationResult second = accumulator.snapshot();

    assertThat(first.scan()).isEqualTo(new ScanSummary(1, 0));
    assertThat(second.scan()).isEqualTo(new ScanSummary(3, 0));
  }

  @Test
  void rejectsInvalidInputs() {
    ContentCanonicalizationResultAccumulator accumulator =
        new ContentCanonicalizationResultAccumulator();

    assertThatThrownBy(() -> accumulator.addScannedRows(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scannedRows");
    assertThatThrownBy(() -> accumulator.addUnchangedRows(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unchangedRows");
    assertThatThrownBy(() -> accumulator.recordPlannedGroup(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("plan");
    assertThatThrownBy(() -> accumulator.addMergeCounts(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("counts");
    assertThatThrownBy(() -> accumulator.addFileSummary(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("files");
    assertThatThrownBy(() -> accumulator.addMergeCounts(new MergeCounts(-1, 0, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deletedContentHeaders");
  }

  private ContentCanonicalizationPlan plan(int memberCount, boolean feedConflict) {
    List<ContentCanonicalizationCandidate> members =
        java.util.stream.IntStream.range(0, memberCount)
            .mapToObj(i -> candidate(Character.toString('a' + i)))
            .toList();
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
    return new ContentCanonicalizationPlan(group, survivor, Optional.empty(), feedConflict);
  }

  private ContentCanonicalizationCandidate candidate(String suffix) {
    ContentCanonicalizationHeader header =
        new ContentCanonicalizationHeader(
            "0000000000000000000000000000000" + suffix,
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
}
