package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FeedEntrySelectionTest {

  @Test
  void factoriesReturnCorrespondingSubtypesWithoutPayload() {
    assertThat(FeedEntrySelection.first()).isInstanceOf(FeedEntrySelection.First.class);
    assertThat(FeedEntrySelection.latest()).isInstanceOf(FeedEntrySelection.Latest.class);
    assertThat(FeedEntrySelection.index(0)).isEqualTo(new FeedEntrySelection.ByIndex(0));
    assertThat(FeedEntrySelection.index(3)).isEqualTo(new FeedEntrySelection.ByIndex(3));
    assertThat(FeedEntrySelection.titleRegex("a.*"))
        .isEqualTo(new FeedEntrySelection.ByTitleRegex("a.*"));
    assertThat(FeedEntrySelection.urlRegex("/posts/\\d+"))
        .isEqualTo(new FeedEntrySelection.ByUrlRegex("/posts/\\d+"));
  }

  @Test
  void indexRejectsNegativeValues() {
    assertThatThrownBy(() -> FeedEntrySelection.index(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(">= 0")
        .hasMessageContaining("-1");
  }

  @Test
  void titleAndUrlRegexAcceptValidPatternsAndEqualByString() {
    assertThat(FeedEntrySelection.titleRegex("Release .*"))
        .isEqualTo(FeedEntrySelection.titleRegex("Release .*"));
    assertThat(FeedEntrySelection.urlRegex("https://example\\.test/.*"))
        .isEqualTo(FeedEntrySelection.urlRegex("https://example\\.test/.*"));
  }

  @Test
  void titleRegexRejectsNullBlankAndInvalidPatterns() {
    assertThatThrownBy(() -> FeedEntrySelection.titleRegex(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("title regex");
    assertThatThrownBy(() -> FeedEntrySelection.titleRegex(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
    assertThatThrownBy(() -> FeedEntrySelection.titleRegex("["))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid Java regex");
  }

  @Test
  void urlRegexRejectsNullBlankAndInvalidPatterns() {
    assertThatThrownBy(() -> FeedEntrySelection.urlRegex(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("url regex");
    assertThatThrownBy(() -> FeedEntrySelection.urlRegex("\t"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
    assertThatThrownBy(() -> FeedEntrySelection.urlRegex("*"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid Java regex");
  }

  @Test
  void hasNoKindPlusNullablePayloadConstructor() {
    assertThat(FeedEntrySelection.class.getDeclaredClasses())
        .extracting(Class::getSimpleName)
        .contains("First", "Latest", "ByIndex", "ByTitleRegex", "ByUrlRegex")
        .doesNotContain("Kind");
  }
}
