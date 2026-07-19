package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndEntry;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Feed-level orchestration; each entry's mapping and persistence is delegated. */
@Service
public class FeedEntryImportService {
  private static final Logger logger = LoggerFactory.getLogger(FeedEntryImportService.class);
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

  public int importEntries(FeedUrl feedUrl) {
    try {
      int inserted = 0;
      for (SyndEntry entry :
          feedDocumentService.fetch(java.net.URI.create(feedUrl.url())).getEntries()) {
        if (entryImporter.importEntry(feedUrl, entry)) {
          inserted++;
        }
      }
      return inserted;
    } catch (Exception e) {
      if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      logger.warn("Failed to import feed {}", feedUrl.url(), e);
      return 0;
    }
  }
}
