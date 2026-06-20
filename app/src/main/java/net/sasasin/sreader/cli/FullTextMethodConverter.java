package net.sasasin.sreader.cli;

import net.sasasin.sreader.domain.FullTextMethod;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class FullTextMethodConverter implements ITypeConverter<FullTextMethod> {

  @Override
  public FullTextMethod convert(String value) throws Exception {
    if (value == null || value.isBlank()) {
      throw new TypeConversionException("Method must not be blank");
    }
    try {
      return FullTextMethod.fromValue(value.trim());
    } catch (IllegalArgumentException e) {
      throw new TypeConversionException(
          "Invalid --method value '"
              + value
              + "'. Valid values: feed, http, playwright, playwright_readability,"
              + " playwright_infy_scroll, playwright_infy_scroll_readability");
    }
  }
}
