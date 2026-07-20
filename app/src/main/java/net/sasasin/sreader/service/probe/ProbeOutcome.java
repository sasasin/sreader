package net.sasasin.sreader.service.probe;

import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.extraction.NoContentReason;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.outcome.OutcomePreconditions;

/** Outcome of a probe article or probe feed operation. */
public sealed interface ProbeOutcome
    permits ProbeOutcome.Succeeded,
        ProbeOutcome.NoContent,
        ProbeOutcome.NoMatchingEntry,
        ProbeOutcome.Skipped,
        ProbeOutcome.InvalidRequest,
        ProbeOutcome.Failed {

  record Succeeded(ProbeDocument document, String text) implements ProbeOutcome {
    public Succeeded {
      Objects.requireNonNull(document, "document must not be null");
      text = OutcomePreconditions.requireNonBlank(text, "text");
    }
  }

  record NoContent(ProbeDocument document, NoContentReason reason) implements ProbeOutcome {
    public NoContent {
      Objects.requireNonNull(document, "document must not be null");
      Objects.requireNonNull(reason, "reason must not be null");
    }
  }

  record NoMatchingEntry(String message) implements ProbeOutcome {
    public NoMatchingEntry {
      message = OutcomePreconditions.requireNonBlank(message, "message");
    }
  }

  record Skipped(ProbeSkipReason reason, String message) implements ProbeOutcome {
    public Skipped {
      Objects.requireNonNull(reason, "reason must not be null");
      message = OutcomePreconditions.requireNonBlank(message, "message");
    }
  }

  record InvalidRequest(String message, Optional<Throwable> cause) implements ProbeOutcome {
    public InvalidRequest {
      message = OutcomePreconditions.requireNonBlank(message, "message");
      cause = Objects.requireNonNull(cause, "cause must not be null");
    }

    public static InvalidRequest of(String message) {
      return new InvalidRequest(message, Optional.empty());
    }
  }

  record Failed(OperationFailure failure) implements ProbeOutcome {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
    }
  }
}
