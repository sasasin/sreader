package net.sasasin.sreader.cli;

import net.sasasin.sreader.domain.FeedEntrySelection;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converts {@code --entry-index <N>} to a {@link FeedEntrySelection}. */
final class EntryIndexSelectionConverter implements ITypeConverter<FeedEntrySelection> {

  @Override
  public FeedEntrySelection convert(String value) {
    if (value == null || value.isBlank()) {
      throw new TypeConversionException("--entry-index must be a non-negative integer: " + value);
    }
    String trimmed = value.trim();
    final int index;
    try {
      index = Integer.parseInt(trimmed);
    } catch (NumberFormatException e) {
      throw new TypeConversionException("--entry-index must be a non-negative integer: " + value);
    }
    try {
      return FeedEntrySelection.index(index);
    } catch (IllegalArgumentException e) {
      throw new TypeConversionException("--entry-index must be a non-negative integer: " + value);
    }
  }
}
