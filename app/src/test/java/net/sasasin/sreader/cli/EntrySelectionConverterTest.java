package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sasasin.sreader.domain.FeedEntrySelection;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.TypeConversionException;

class EntrySelectionConverterTest {

  private final EntryPositionSelectionConverter position = new EntryPositionSelectionConverter();
  private final EntryIndexSelectionConverter index = new EntryIndexSelectionConverter();
  private final EntryTitleRegexSelectionConverter title = new EntryTitleRegexSelectionConverter();
  private final EntryUrlRegexSelectionConverter url = new EntryUrlRegexSelectionConverter();

  @Test
  void positionConvertsFirstAndLatestCaseInsensitivelyWithTrim() {
    assertThat(position.convert(" FiRsT ")).isEqualTo(FeedEntrySelection.first());
    assertThat(position.convert("LATEST")).isEqualTo(FeedEntrySelection.latest());
  }

  @Test
  void positionRejectsBlankAndUnknown() {
    assertThatThrownBy(() -> position.convert(null))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry");
    assertThatThrownBy(() -> position.convert(""))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry");
    assertThatThrownBy(() -> position.convert("newest"))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry");
  }

  @Test
  void indexConvertsNonNegativeIntegers() {
    assertThat(index.convert("0")).isEqualTo(FeedEntrySelection.index(0));
    assertThat(index.convert(" 12 ")).isEqualTo(FeedEntrySelection.index(12));
  }

  @Test
  void indexRejectsBlankNegativeNonIntegerAndOverflow() {
    assertThatThrownBy(() -> index.convert(null))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-index");
    assertThatThrownBy(() -> index.convert(""))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-index");
    assertThatThrownBy(() -> index.convert("-1"))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-index");
    assertThatThrownBy(() -> index.convert("1.5"))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-index");
    assertThatThrownBy(() -> index.convert("99999999999999999999"))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-index");
  }

  @Test
  void titleRegexKeepsOriginalPatternAndRejectsBlankOrInvalid() {
    assertThat(title.convert("Release .*")).isEqualTo(FeedEntrySelection.titleRegex("Release .*"));
    assertThatThrownBy(() -> title.convert(null))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-title-regex");
    assertThatThrownBy(() -> title.convert("  "))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-title-regex");
    assertThatThrownBy(() -> title.convert("["))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-title-regex");
  }

  @Test
  void urlRegexKeepsOriginalPatternAndRejectsBlankOrInvalid() {
    assertThat(url.convert("/posts/[0-9]+"))
        .isEqualTo(FeedEntrySelection.urlRegex("/posts/[0-9]+"));
    assertThatThrownBy(() -> url.convert(null))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-url-regex");
    assertThatThrownBy(() -> url.convert(""))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-url-regex");
    assertThatThrownBy(() -> url.convert("*"))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("--entry-url-regex");
  }
}
