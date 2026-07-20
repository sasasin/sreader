package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedTomlIssueTest {

  @Test
  void rejectsNullKindAndBlankMessage() {
    assertThatThrownBy(
            () -> new FeedTomlIssue(null, FeedTomlPosition.unknown(), null, "message", 0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new FeedTomlIssue(
                    FeedTomlIssue.Kind.SCHEMA, FeedTomlPosition.unknown(), null, " ", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message");
  }

  @Test
  void positionRequiresBothLineAndColumnOrNeither() {
    assertThatThrownBy(() -> FeedTomlPosition.of(0, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FeedTomlPosition.of(1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("column");
    assertThat(FeedTomlPosition.unknown().isKnown()).isFalse();
    assertThat(FeedTomlPosition.of(2, 3).line().getAsInt()).isEqualTo(2);
  }

  @Test
  void sortsKnownPositionsBeforeUnknownThenByKindAndSequence() {
    FeedTomlIssue unknownDomain =
        FeedTomlIssue.domain(FeedTomlPosition.unknown(), "p", "unknown domain", 0);
    FeedTomlIssue line2Schema =
        FeedTomlIssue.schema(FeedTomlPosition.of(2, 1), "p", "schema at 2", 5);
    FeedTomlIssue line1Syntax = FeedTomlIssue.syntax(FeedTomlPosition.of(1, 5), "syntax at 1", 9);
    FeedTomlIssue line1Domain =
        FeedTomlIssue.domain(FeedTomlPosition.of(1, 5), "p", "domain at 1", 1);
    FeedTomlIssue line1Schema =
        FeedTomlIssue.schema(FeedTomlPosition.of(1, 5), "p", "schema at 1", 2);

    List<String> rendered =
        FeedTomlIssue.renderAll(
            List.of(unknownDomain, line2Schema, line1Domain, line1Schema, line1Syntax));

    assertThat(rendered)
        .containsExactly(
            "line 1, column 5: syntax at 1",
            "line 1, column 5: schema at 1",
            "line 1, column 5: domain at 1",
            "line 2, column 1: schema at 2",
            "unknown domain");
  }

  @Test
  void renderAllReturnsImmutableList() {
    List<FeedTomlIssue> mutable =
        new ArrayList<>(
            List.of(
                FeedTomlIssue.schema(
                    FeedTomlPosition.unknown(), "x", "schema_version is required", 0)));
    List<String> rendered = FeedTomlIssue.renderAll(mutable);
    assertThatThrownBy(() -> rendered.add("nope"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void positionRejectsMismatchedOptionalPresence() {
    assertThatThrownBy(
            () -> new FeedTomlPosition(java.util.OptionalInt.of(1), java.util.OptionalInt.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both");
  }

  @Test
  void entryRejectsNegativeIndex() {
    assertThatThrownBy(
            () ->
                new FeedTomlEntry(
                    -1,
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    FeedTomlPosition.unknown(),
                    FeedTomlPosition.unknown()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("index");
  }
}
