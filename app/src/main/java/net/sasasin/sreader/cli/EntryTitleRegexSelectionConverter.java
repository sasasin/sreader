package net.sasasin.sreader.cli;

import net.sasasin.sreader.domain.FeedEntrySelection;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converts {@code --entry-title-regex <REGEX>} to a {@link FeedEntrySelection}. */
final class EntryTitleRegexSelectionConverter implements ITypeConverter<FeedEntrySelection> {

  @Override
  public FeedEntrySelection convert(String value) {
    if (value == null || value.isBlank()) {
      throw new TypeConversionException("--entry-title-regex must not be blank");
    }
    try {
      // Keep original value (no trim of pattern); domain validates syntax.
      return FeedEntrySelection.titleRegex(value);
    } catch (IllegalArgumentException e) {
      throw new TypeConversionException(
          "--entry-title-regex is not a valid regular expression: " + value);
    }
  }
}
