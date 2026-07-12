package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FeedStatusTest {

  @Test
  void activeValue() {
    assertThat(FeedStatus.ACTIVE.value()).isEqualTo("active");
  }

  @Test
  void unsubscribedValue() {
    assertThat(FeedStatus.UNSUBSCRIBED.value()).isEqualTo("unsubscribed");
  }

  @Test
  void fromValueActive() {
    assertThat(FeedStatus.fromValue("active")).isEqualTo(FeedStatus.ACTIVE);
  }

  @Test
  void fromValueUnsubscribed() {
    assertThat(FeedStatus.fromValue("unsubscribed")).isEqualTo(FeedStatus.UNSUBSCRIBED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "ACTIVE", "Active", "unknown", "http"})
  void fromValueRejectsUnsupportedValues(String value) {
    assertThatThrownBy(() -> FeedStatus.fromValue(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported feed status: " + value);
  }
}
