package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExtractionPlanTest {

  @Test
  void parsesFullTextMethodsFromValues() {
    for (FullTextMethod method : FullTextMethod.values()) {
      assertThat(FullTextMethod.fromValue(method.value())).isEqualTo(method);
    }
  }

  @Test
  void rejectsUnknownFullTextMethod() {
    assertThatThrownBy(() -> FullTextMethod.fromValue("unknown"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mapsMethodsToExtractionPlans() {
    assertThat(ExtractionPlan.from(FullTextMethod.FEED))
        .isEqualTo(
            new ExtractionPlan(
                ExtractionPlan.SourceKind.FEED,
                false,
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT));
    assertThat(ExtractionPlan.from(FullTextMethod.HTTP).sourceKind())
        .isEqualTo(ExtractionPlan.SourceKind.HTTP);
    assertThat(ExtractionPlan.from(FullTextMethod.HTTP_READABILITY))
        .isEqualTo(
            new ExtractionPlan(
                ExtractionPlan.SourceKind.HTTP, false, ExtractionPlan.ExtractorKind.READABILITY));
    assertThat(ExtractionPlan.from(FullTextMethod.PLAYWRIGHT_READABILITY).extractorKind())
        .isEqualTo(ExtractionPlan.ExtractorKind.READABILITY);
    assertThat(ExtractionPlan.from(FullTextMethod.PLAYWRIGHT_INFY_SCROLL).useInfyScroll()).isTrue();
    assertThat(
            ExtractionPlan.from(FullTextMethod.PLAYWRIGHT_INFY_SCROLL_READABILITY).extractorKind())
        .isEqualTo(ExtractionPlan.ExtractorKind.READABILITY);
  }
}
