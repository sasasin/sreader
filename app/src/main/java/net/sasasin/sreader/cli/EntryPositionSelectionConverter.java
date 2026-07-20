package net.sasasin.sreader.cli;

import java.util.Locale;
import net.sasasin.sreader.domain.FeedEntrySelection;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converts {@code --entry first|latest} to a {@link FeedEntrySelection}. */
final class EntryPositionSelectionConverter implements ITypeConverter<FeedEntrySelection> {

  @Override
  public FeedEntrySelection convert(String value) {
    if (value == null || value.isBlank()) {
      throw new TypeConversionException("--entry must be first or latest");
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "first" -> FeedEntrySelection.first();
      case "latest" -> FeedEntrySelection.latest();
      default -> throw new TypeConversionException("--entry must be first or latest: " + value);
    };
  }
}
