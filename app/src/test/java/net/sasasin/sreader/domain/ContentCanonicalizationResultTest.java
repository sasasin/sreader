package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.IntFunction;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.DatabaseSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.FileSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.GroupSummary;
import net.sasasin.sreader.domain.ContentCanonicalizationResult.ScanSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ContentCanonicalizationResultTest {

  @Test
  void emptyHasZeroCountersAndNoFailures() {
    ContentCanonicalizationResult result = ContentCanonicalizationResult.empty();

    assertThat(result.scan()).isEqualTo(ScanSummary.empty());
    assertThat(result.groups()).isEqualTo(GroupSummary.empty());
    assertThat(result.database()).isEqualTo(DatabaseSummary.empty());
    assertThat(result.files()).isEqualTo(FileSummary.empty());
    assertThat(result.groups().processedGroups()).isZero();
    assertThat(result.hasFailures()).isFalse();
  }

  @Test
  void groupedConstructionPreservesSummariesAndDerivedProcessedGroups() {
    ContentCanonicalizationResult result =
        new ContentCanonicalizationResult(
            new ScanSummary(10, 3),
            new GroupSummary(2, 4, 1, 0),
            new DatabaseSummary(5, 6, 7),
            new FileSummary(8, 9, 0));

    assertThat(result.scan()).isEqualTo(new ScanSummary(10, 3));
    assertThat(result.groups()).isEqualTo(new GroupSummary(2, 4, 1, 0));
    assertThat(result.database()).isEqualTo(new DatabaseSummary(5, 6, 7));
    assertThat(result.files()).isEqualTo(new FileSummary(8, 9, 0));
    assertThat(result.groups().processedGroups()).isEqualTo(6);
    assertThat(result.hasFailures()).isFalse();
  }

  @Test
  void processedGroupsIsRenamePlusMergeOnly() {
    assertThat(new GroupSummary(3, 0, 0, 0).processedGroups()).isEqualTo(3);
    assertThat(new GroupSummary(0, 5, 0, 0).processedGroups()).isEqualTo(5);
    assertThat(new GroupSummary(2, 3, 0, 0).processedGroups()).isEqualTo(5);
    assertThat(new GroupSummary(1, 1, 9, 7).processedGroups()).isEqualTo(2);
  }

  @Test
  void hasFailuresWhenFailedFilesOrFailedGroups() {
    assertThat(
            new ContentCanonicalizationResult(
                    ScanSummary.empty(),
                    GroupSummary.empty(),
                    DatabaseSummary.empty(),
                    new FileSummary(0, 0, 1))
                .hasFailures())
        .isTrue();
    assertThat(
            new ContentCanonicalizationResult(
                    ScanSummary.empty(),
                    new GroupSummary(0, 0, 0, 1),
                    DatabaseSummary.empty(),
                    FileSummary.empty())
                .hasFailures())
        .isTrue();
  }

  @Test
  void hasFailuresIsFalseForMissingFilesDatabaseDeletesAndFeedConflicts() {
    assertThat(
            new ContentCanonicalizationResult(
                    ScanSummary.empty(),
                    GroupSummary.empty(),
                    DatabaseSummary.empty(),
                    new FileSummary(0, 3, 0))
                .hasFailures())
        .isFalse();
    assertThat(
            new ContentCanonicalizationResult(
                    ScanSummary.empty(),
                    GroupSummary.empty(),
                    new DatabaseSummary(4, 5, 6),
                    FileSummary.empty())
                .hasFailures())
        .isFalse();
    assertThat(
            new ContentCanonicalizationResult(
                    ScanSummary.empty(),
                    new GroupSummary(0, 0, 2, 0),
                    DatabaseSummary.empty(),
                    FileSummary.empty())
                .hasFailures())
        .isFalse();
  }

  @Test
  void rejectsNullComponents() {
    ScanSummary scan = ScanSummary.empty();
    GroupSummary groups = GroupSummary.empty();
    DatabaseSummary database = DatabaseSummary.empty();
    FileSummary files = FileSummary.empty();

    assertThatThrownBy(() -> new ContentCanonicalizationResult(null, groups, database, files))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("scan");
    assertThatThrownBy(() -> new ContentCanonicalizationResult(scan, null, database, files))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("groups");
    assertThatThrownBy(() -> new ContentCanonicalizationResult(scan, groups, null, files))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("database");
    assertThatThrownBy(() -> new ContentCanonicalizationResult(scan, groups, database, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("files");
  }

  @ParameterizedTest
  @MethodSource("negativeCounterCases")
  void rejectsNegativeCounters(NegativeCase negativeCase) {
    assertThatThrownBy(() -> negativeCase.constructor().apply(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(negativeCase.fieldName())
        .hasMessageContaining("-1");
  }

  static java.util.stream.Stream<NegativeCase> negativeCounterCases() {
    return java.util.stream.Stream.of(
        new NegativeCase("scannedRows", v -> new ScanSummary(v, 0)),
        new NegativeCase("unchangedRows", v -> new ScanSummary(0, v)),
        new NegativeCase("renameGroups", v -> new GroupSummary(v, 0, 0, 0)),
        new NegativeCase("mergeGroups", v -> new GroupSummary(0, v, 0, 0)),
        new NegativeCase("feedConflictGroups", v -> new GroupSummary(0, 0, v, 0)),
        new NegativeCase("failedGroups", v -> new GroupSummary(0, 0, 0, v)),
        new NegativeCase("deletedContentHeaders", v -> new DatabaseSummary(v, 0, 0)),
        new NegativeCase("deletedFullTexts", v -> new DatabaseSummary(0, v, 0)),
        new NegativeCase("deletedExportHistories", v -> new DatabaseSummary(0, 0, v)),
        new NegativeCase("deletedFiles", v -> new FileSummary(v, 0, 0)),
        new NegativeCase("missingFiles", v -> new FileSummary(0, v, 0)),
        new NegativeCase("failedFiles", v -> new FileSummary(0, 0, v)));
  }

  record NegativeCase(String fieldName, IntFunction<Object> constructor) {
    @Override
    public String toString() {
      return fieldName;
    }
  }
}
