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
   * method used multi-page tracking (or single-page fallback after rule miss).
   */
  record Extracted(
      String text, ExtractionDecision decision, Optional<PaginationMetadata> pagination)
      implements TextExtractionOutcome {

    public Extracted(String text, ExtractionDecision decision) {
      this(text, decision, Optional.empty());
    }

    public Extracted {
      text = OutcomePreconditions.requireNonBlank(text, "text");
      Objects.requireNonNull(decision, "decision must not be null");
      Objects.requireNonNull(pagination, "pagination must not be null");
    }

    public Extracted withPagination(PaginationMetadata metadata) {
      return new Extracted(text, decision, Optional.of(metadata));
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

  record Failed(OperationFailure failure) implements TextExtractionOutcome {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
    }
  }
}
