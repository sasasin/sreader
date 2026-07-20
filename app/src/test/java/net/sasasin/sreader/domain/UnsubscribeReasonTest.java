package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UnsubscribeReasonTest {

  @ParameterizedTest
  @EnumSource(UnsubscribeReason.class)
  void roundTripsEveryValue(UnsubscribeReason reason) {
    assertThat(UnsubscribeReason.fromValue(reason.value())).isEqualTo(reason);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"unknown", "NOT_INTERESTED", "other "})
  void rejectsUnsupportedValues(String value) {
    assertThatThrownBy(() -> UnsubscribeReason.fromValue(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported unsubscribe reason");
  }

  @Test
  void exposesWireValues() {
    assertThat(UnsubscribeReason.NOT_INTERESTED.value()).isEqualTo("not_interested");
    assertThat(UnsubscribeReason.OTHER.value()).isEqualTo("other");
  }
}
