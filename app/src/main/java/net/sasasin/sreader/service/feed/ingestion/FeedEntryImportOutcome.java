package net.sasasin.sreader.service.feed.ingestion;

import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.extraction.ContentFullTextWriteOutcome;
import net.sasasin.sreader.service.http.RedirectResolution;
import net.sasasin.sreader.service.outcome.OperationFailure;

/** Outcome of importing a single feed entry. */
public sealed interface FeedEntryImportOutcome
    permits FeedEntryImportOutcome.Inserted,
        FeedEntryImportOutcome.AlreadyPresent,
        FeedEntryImportOutcome.MissingLink,
        FeedEntryImportOutcome.Failed {

  record Inserted(RedirectResolution redirect, Optional<ContentFullTextWriteOutcome> feedTextWrite)
      implements FeedEntryImportOutcome {
    public Inserted {
      Objects.requireNonNull(redirect, "redirect must not be null");
      feedTextWrite = Objects.requireNonNull(feedTextWrite, "feedTextWrite must not be null");
    }
  }

  record AlreadyPresent(RedirectResolution redirect) implements FeedEntryImportOutcome {
    public AlreadyPresent {
      Objects.requireNonNull(redirect, "redirect must not be null");
    }
  }

  record MissingLink() implements FeedEntryImportOutcome {}

  record Failed(OperationFailure failure) implements FeedEntryImportOutcome {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
    }
  }
}
