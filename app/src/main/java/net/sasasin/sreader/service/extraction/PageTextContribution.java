package net.sasasin.sreader.service.extraction;

import java.util.Objects;
import java.util.Optional;

/** Per-page text contribution metadata for paginated extraction. */
public record PageTextContribution(
    int pageNumber,
    ExtractionSource source,
    Optional<ExtractionFallbackReason> fallbackReason,
    String text) {

  public PageTextContribution {
    if (pageNumber < 1) {
      throw new IllegalArgumentException("pageNumber must be >= 1");
    }
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(fallbackReason, "fallbackReason must not be null");
    Objects.requireNonNull(text, "text must not be null");
  }
}
