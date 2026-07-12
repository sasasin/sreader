package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.TypeConversionException;

class FullTextMethodConverterTest {

  private final FullTextMethodConverter converter = new FullTextMethodConverter();

  @Test
  void convertsHttpReadability() throws Exception {
    assertThat(converter.convert("http_readability")).isEqualTo(FullTextMethod.HTTP_READABILITY);
  }

  @Test
  void includesHttpReadabilityInInvalidValueMessage() {
    assertThatThrownBy(() -> converter.convert("invalid"))
        .isInstanceOf(TypeConversionException.class)
        .hasMessageContaining("http_readability");
  }
}
