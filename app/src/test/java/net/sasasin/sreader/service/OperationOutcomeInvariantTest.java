package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Optional;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;

class OperationOutcomeInvariantTest {

  @Test
  void operationFailureRejectsNullAndBlankComponents() {
    assertThatThrownBy(() -> new OperationFailure(null, FailureKind.IO, "s", "m", Optional.empty()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> OperationFailure.of(FailureStage.FETCH_ARTICLE, FailureKind.IO, " ", "message"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> OperationFailure.of(FailureStage.FETCH_ARTICLE, FailureKind.IO, "subject", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void extractedTextMustBeNonBlank() {
    assertThatThrownBy(
            () ->
                new TextExtractionOutcome.Extracted(
                    " ", ExtractionDecision.of(ExtractionSource.BODY_TEXT)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void probeSucceededTextMustBeNonBlank() {
    ProbeDocument document =
        new ProbeDocument(
            URI.create("https://example.com"),
            URI.create("https://example.com"),
            Optional.empty(),
            FullTextMethod.HTTP);
    assertThatThrownBy(() -> new ProbeOutcome.Succeeded(document, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void batchResultRejectsCountsExceedingSelectedTargets() {
    assertThatThrownBy(() -> new FullTextExtractionBatchResult(1, 1, 1, 0, 0, 0, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void redirectFallbackEffectiveUriIsRequested() {
    URI uri = URI.create("https://example.com/a");
    RedirectResolution.Fallback fallback =
        new RedirectResolution.Fallback(
            uri,
            OperationFailure.of(
                FailureStage.RESOLVE_REDIRECT, FailureKind.IO, uri.toString(), "down"));
    assertThat(fallback.effectiveUri()).isEqualTo(uri);
    assertThat(fallback.failure().kind()).isEqualTo(FailureKind.IO);
  }
}
