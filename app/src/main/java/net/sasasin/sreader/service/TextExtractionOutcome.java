package net.sasasin.sreader.service;

import java.util.Objects;

/** Outcome of extracting article or feed full text. */
public sealed interface TextExtractionOutcome
    permits TextExtractionOutcome.Extracted,
        TextExtractionOutcome.NoContent,
        TextExtractionOutcome.Skipped,
        TextExtractionOutcome.Failed {

  record Extracted(String text, ExtractionDecision decision) implements TextExtractionOutcome {
    public Extracted {
      text = OutcomePreconditions.requireNonBlank(text, "text");
      Objects.requireNonNull(decision, "decision must not be null");
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
