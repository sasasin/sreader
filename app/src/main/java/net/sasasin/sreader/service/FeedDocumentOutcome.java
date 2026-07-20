package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import java.util.List;
import java.util.Objects;

/** Outcome of fetching and parsing a feed document. */
public sealed interface FeedDocumentOutcome
    permits FeedDocumentOutcome.Fetched, FeedDocumentOutcome.Failed {

  record Fetched(SyndFeed feed) implements FeedDocumentOutcome {
    public Fetched {
      Objects.requireNonNull(feed, "feed must not be null");
    }

    public List<SyndEntry> entries() {
      List<SyndEntry> entries = feed.getEntries();
      return entries == null ? List.of() : List.copyOf(entries);
    }
  }

  record Failed(OperationFailure failure) implements FeedDocumentOutcome {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
    }
  }
}
