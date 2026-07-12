package net.sasasin.sreader.cli;

import java.util.Arrays;
import java.util.stream.Collectors;
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
              + "'. Valid values: "
              + Arrays.stream(FullTextMethod.values())
                  .map(FullTextMethod::value)
                  .collect(Collectors.joining(", ")));
    }
  }
}
