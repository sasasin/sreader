package net.sasasin.sreader.service.extraction;

import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.outcome.OutcomePreconditions;

/** Outcome of extracting article or feed full text. */
public sealed interface TextExtractionOutcome
    permits TextExtractionOutcome.Extracted,
        TextExtractionOutcome.NoContent,
        TextExtractionOutcome.Skipped,
        TextExtractionOutcome.Failed {

  /**
   * Successful extraction. Optional {@link #pagination()} carries AutoPagerize metadata when the
   * method used multi-page tracking (or single-page fallback after rule miss). {@link
   * #extractedUrl()} is the first-page final/extracted URL when known.
   */
  record Extracted(
      String text,
      ExtractionDecision decision,
      Optional<PaginationMetadata> pagination,
      Optional<String> extractedUrl)
      implements TextExtractionOutcome {

    public Extracted(String text, ExtractionDecision decision) {
      this(text, decision, Optional.empty(), Optional.empty());
    }

    public Extracted(
        String text, ExtractionDecision decision, Optional<PaginationMetadata> pagination) {
      this(text, decision, pagination, Optional.empty());
    }

    public Extracted {
      text = OutcomePreconditions.requireNonBlank(text, "text");
      Objects.requireNonNull(decision, "decision must not be null");
      Objects.requireNonNull(pagination, "pagination must not be null");
      Objects.requireNonNull(extractedUrl, "extractedUrl must not be null");
      extractedUrl = extractedUrl.map(String::trim).filter(s -> !s.isEmpty());
    }

    public Extracted withPagination(PaginationMetadata metadata) {
      return new Extracted(text, decision, Optional.of(metadata), extractedUrl);
    }

    public Extracted withExtractedUrl(String url) {
      return new Extracted(text, decision, pagination, Optional.of(url));
    }
  }

  record NoContent(NoContentReason reason, ExtractionDecision decision)
      implements TextExtractionOutcome {
    public NoContent {
      Objects.requireNonNull(reason, "reason must not be null");
      Objects.requireNonNull(decision, "decision must not be null");
    }
  }

  record Skipped(TextExtractionSkipReason reason) implements TextExtractionOutcome {
    public Skipped {
      Objects.requireNonNull(reason, "reason must not be null");
    }
  }

  /**
   * Extraction failure. Optional {@link #pagination()} carries partial page traces when
   * AutoPagerize failed after loading one or more pages (never used for successful partial text
   * persistence).
   */
  record Failed(OperationFailure failure, Optional<PaginationMetadata> pagination)
      implements TextExtractionOutcome {
    public Failed(OperationFailure failure) {
      this(failure, Optional.empty());
    }

    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
      Objects.requireNonNull(pagination, "pagination must not be null");
    }

    public Failed withPagination(PaginationMetadata metadata) {
      return new Failed(failure, Optional.of(metadata));
    }
  }
}
