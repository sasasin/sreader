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

  boolean importEntry(FeedUrl feedUrl, SyndEntry entry) throws Exception {
    if (entry.getLink() == null || entry.getLink().isBlank()) {
      return false;
    }
    URI source = URI.create(entry.getLink());
    URI fetch = httpFetchService.resolveRedirect(source);
    URI canonical = canonicalizer.canonicalize(fetch);
    Optional<String> feedText =
        feedUrl.fullTextMethod() == FullTextMethod.FEED
            ? feedTextExtractor.extract(entry)
            : Optional.empty();
    ContentHeader header =
        new ContentHeader(
            HashIds.md5(canonical.toString()),
            feedUrl.id(),
            source.toString(),
            fetch.toString(),
            canonical.toString(),
            entry.getTitle(),
            toOffsetDateTime(entry.getPublishedDate()),
            feedText.orElse(null));
    if (!headers.insertOrRefreshFetchUrl(header)) {
      return false;
    }
    if (feedUrl.fullTextMethod() == FullTextMethod.FEED) {
      feedText.ifPresent(text -> fullTextWriter.saveIfAbsent(header, text));
    }
    return true;
  }

  private OffsetDateTime toOffsetDateTime(Date date) {
    return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
  }
}
