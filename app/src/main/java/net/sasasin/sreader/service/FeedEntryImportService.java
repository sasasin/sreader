package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndEntry;
import java.net.URI;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Feed-level orchestration; each entry's mapping and persistence is delegated. */
@Service
public class FeedEntryImportService {
  private final FeedDocumentService feedDocumentService;
  private final FeedEntryImporter entryImporter;

  @Autowired
  public FeedEntryImportService(
      HttpFetchService httpFetchService,
      ArticleUrlCanonicalizer canonicalizer,
      ContentHeaderRepository headers,
      FeedEntryFullTextExtractor feedTextExtractor,
      ContentFullTextWriter fullTextWriter) {
    this(
        new FeedDocumentService(httpFetchService),
        new FeedEntryImporter(
            httpFetchService, canonicalizer, headers, feedTextExtractor, fullTextWriter));
  }

  FeedEntryImportService(FeedDocumentService feedDocumentService, FeedEntryImporter entryImporter) {
    this.feedDocumentService = feedDocumentService;
    this.entryImporter = entryImporter;
  }

  public FeedImportResult importEntries(FeedUrl feedUrl) {
    FeedDocumentOutcome document = feedDocumentService.fetch(URI.create(feedUrl.url()));
    return switch (document) {
      case FeedDocumentOutcome.Failed failed ->
          new FeedImportResult.Failed(FeedImportSummary.empty(), failed.failure());
      case FeedDocumentOutcome.Fetched fetched -> importFetchedEntries(feedUrl, fetched);
    };
  }

  private FeedImportResult importFetchedEntries(
      FeedUrl feedUrl, FeedDocumentOutcome.Fetched fetched) {
    FeedImportSummary summary = FeedImportSummary.empty();
    for (SyndEntry entry : fetched.entries()) {
      FeedEntryImportOutcome outcome = entryImporter.importEntry(feedUrl, entry);
      summary = summary.plusEntry(outcome);
      if (outcome instanceof FeedEntryImportOutcome.Failed failed) {
        // Preserve current feed-level abort semantics for fatal entry failures.
        return new FeedImportResult.Failed(summary, failed.failure());
      }
    }
    return new FeedImportResult.Completed(summary);
  }
}
