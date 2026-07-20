package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndEntry;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.springframework.dao.DataAccessException;

/** Maps and persists one parsed feed entry. */
final class FeedEntryImporter {
  private final HttpFetchService httpFetchService;
  private final ArticleUrlCanonicalizer canonicalizer;
  private final ContentHeaderRepository headers;
  private final FeedEntryFullTextExtractor feedTextExtractor;
  private final ContentFullTextWriter fullTextWriter;

  FeedEntryImporter(
      HttpFetchService httpFetchService,
      ArticleUrlCanonicalizer canonicalizer,
      ContentHeaderRepository headers,
      FeedEntryFullTextExtractor feedTextExtractor,
      ContentFullTextWriter fullTextWriter) {
    this.httpFetchService = httpFetchService;
    this.canonicalizer = canonicalizer;
    this.headers = headers;
    this.feedTextExtractor = feedTextExtractor;
    this.fullTextWriter = fullTextWriter;
  }

  FeedEntryImportOutcome importEntry(FeedUrl feedUrl, SyndEntry entry) {
    if (entry.getLink() == null || entry.getLink().isBlank()) {
      return new FeedEntryImportOutcome.MissingLink();
    }

    final URI source;
    try {
      source = URI.create(entry.getLink());
    } catch (IllegalArgumentException e) {
      return new FeedEntryImportOutcome.Failed(
          OperationFailure.of(
              FailureStage.RESOLVE_REDIRECT,
              FailureKind.INVALID_INPUT,
              entry.getLink(),
              "Invalid entry link: " + entry.getLink(),
              e));
    }

    RedirectResolution redirect = httpFetchService.resolveRedirect(source);
    if (redirect instanceof RedirectResolution.Fallback fallback
        && fallback.failure().interrupted()) {
      return new FeedEntryImportOutcome.Failed(fallback.failure());
    }

    URI fetch = redirect.effectiveUri();
    URI canonical = canonicalizer.canonicalize(fetch);

    String feedTextValue = null;
    if (feedUrl.fullTextMethod() == FullTextMethod.FEED) {
      TextExtractionOutcome feedTextOutcome = feedTextExtractor.extract(entry);
      switch (feedTextOutcome) {
        case TextExtractionOutcome.Extracted extracted -> feedTextValue = extracted.text();
        case TextExtractionOutcome.NoContent ignored -> {
          // The header is still importable; the explicit NO_CONTENT write outcome is recorded
          // below.
        }
        case TextExtractionOutcome.Skipped skipped ->
            throw new IllegalStateException(
                "Feed entry extraction must not be skipped: " + skipped.reason());
        case TextExtractionOutcome.Failed failed -> {
          return new FeedEntryImportOutcome.Failed(failed.failure());
        }
      }
    }

    ContentHeader header =
        new ContentHeader(
            HashIds.md5(canonical.toString()),
            feedUrl.id(),
            source.toString(),
            fetch.toString(),
            canonical.toString(),
            entry.getTitle(),
            toOffsetDateTime(entry.getPublishedDate()),
            feedTextValue);

    final ContentHeaderUpsertOutcome upsert;
    try {
      upsert = headers.insertOrRefreshFetchUrl(header);
    } catch (DataAccessException e) {
      return new FeedEntryImportOutcome.Failed(
          OperationFailure.of(
              FailureStage.PERSIST_HEADER,
              FailureKind.PERSISTENCE,
              header.canonicalUrl(),
              "Failed to persist content header: " + e.getMessage(),
              e));
    }

    if (upsert == ContentHeaderUpsertOutcome.EXISTING_REFRESHED) {
      return new FeedEntryImportOutcome.AlreadyPresent(redirect);
    }

    Optional<ContentFullTextWriteOutcome> feedTextWrite = Optional.empty();
    if (feedUrl.fullTextMethod() == FullTextMethod.FEED) {
      try {
        feedTextWrite = Optional.of(fullTextWriter.saveIfAbsent(header, feedTextValue));
      } catch (DataAccessException e) {
        return new FeedEntryImportOutcome.Failed(
            OperationFailure.of(
                FailureStage.PERSIST_FULL_TEXT,
                FailureKind.PERSISTENCE,
                header.canonicalUrl(),
                "Failed to persist feed full text: " + e.getMessage(),
                e));
      }
    }
    return new FeedEntryImportOutcome.Inserted(redirect, feedTextWrite);
  }

  private OffsetDateTime toOffsetDateTime(Date date) {
    return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
  }
}
