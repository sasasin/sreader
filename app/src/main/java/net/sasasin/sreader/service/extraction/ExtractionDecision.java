package net.sasasin.sreader.service.extraction;

import java.util.Objects;
import java.util.Optional;

/** Metadata describing which extractor produced (or attempted) a text result. */
public record ExtractionDecision(
    ExtractionSource source, Optional<ExtractionFallbackReason> fallbackReason) {

  public ExtractionDecision {
    Objects.requireNonNull(source, "source must not be null");
    fallbackReason = Objects.requireNonNull(fallbackReason, "fallbackReason must not be null");
  }

  public static ExtractionDecision of(ExtractionSource source) {
    return new ExtractionDecision(source, Optional.empty());
  }

  public static ExtractionDecision of(
      ExtractionSource source, ExtractionFallbackReason fallbackReason) {
    return new ExtractionDecision(source, Optional.of(fallbackReason));
  }
}
