package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine.TypeConversionException;

class FullTextMethodConverterTest {

  private final FullTextMethodConverter converter = new FullTextMethodConverter();

  static Stream<FullTextMethod> allMethods() {
    return Arrays.stream(FullTextMethod.values());
  }

  @ParameterizedTest
  @MethodSource("allMethods")
  void convertsExactValue(FullTextMethod method) throws Exception {
    assertThat(converter.convert(method.value())).isEqualTo(method);
  }

  @ParameterizedTest
  @MethodSource("allMethods")
  void convertsValueWithSurroundingWhitespace(FullTextMethod method) throws Exception {
    assertThat(converter.convert("  " + method.value() + "  ")).isEqualTo(method);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  void rejectsBlankValues(String value) {
    assertThatThrownBy(() -> converter.convert(value))
        .isInstanceOf(TypeConversionException.class)
        .hasMessage("Method must not be blank");
  }

  @Test
  void rejectsUnknownValueWithHelpfulMessage() {
    String input = "invalid";
    assertThatThrownBy(() -> converter.convert(input))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("Invalid --method value '" + input + "'")
        .hasMessageContaining("Valid values: " + FullTextMethod.supportedValues())
        .satisfies(
            ex -> {
              for (String wire : FullTextMethod.wireValues()) {
                assertThat(ex.getMessage()).contains(wire);
              }
            });
  }

  @Test
  void validValuesMessageTracksCatalog() {
    assertThat(FullTextMethod.supportedValues())
        .isEqualTo(String.join(", ", FullTextMethod.wireValues()));
  }

  @Test
  void rejectsUppercaseMismatchBecauseFromValueIsCaseSensitive() {
    assertThatThrownBy(() -> converter.convert("HTTP"))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("Invalid --method value 'HTTP'");
  }
}
