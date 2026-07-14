package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeedEntryImportService {

  private static final Logger logger = LoggerFactory.getLogger(FeedEntryImportService.class);

  private final HttpFetchService httpFetchService;
  private final ArticleUrlCanonicalizer articleUrlCanonicalizer;
  private final ContentHeaderRepository contentHeaderRepository;
  private final FeedEntryFullTextExtractor feedEntryFullTextExtractor;
  private final ContentFullTextWriter contentFullTextWriter;

  public FeedEntryImportService(
      HttpFetchService httpFetchService,
      ArticleUrlCanonicalizer articleUrlCanonicalizer,
      ContentHeaderRepository contentHeaderRepository,
      FeedEntryFullTextExtractor feedEntryFullTextExtractor,
      ContentFullTextWriter contentFullTextWriter) {
    this.httpFetchService = httpFetchService;
    this.articleUrlCanonicalizer = articleUrlCanonicalizer;
    this.contentHeaderRepository = contentHeaderRepository;
    this.feedEntryFullTextExtractor = feedEntryFullTextExtractor;
    this.contentFullTextWriter = contentFullTextWriter;
  }

  public int importEntries(FeedUrl feedUrl) {
    try {
      HttpFetchService.FetchedResource feed = httpFetchService.get(URI.create(feedUrl.url()));
      SyndFeed syndFeed =
          new SyndFeedInput()
              .build(
                  new XmlReader(
                      new ByteArrayInputStream(feed.body().getBytes(StandardCharsets.UTF_8))));
      int inserted = 0;
      for (SyndEntry entry : syndFeed.getEntries()) {
        if (entry.getLink() == null || entry.getLink().isBlank()) {
          continue;
        }
        URI sourceUri = URI.create(entry.getLink());
        URI fetchUri = httpFetchService.resolveRedirect(sourceUri);
        URI canonicalUri = articleUrlCanonicalizer.canonicalize(fetchUri);
        Optional<String> feedText =
            feedUrl.fullTextMethod() == FullTextMethod.FEED
                ? feedEntryFullTextExtractor.extract(entry)
                : Optional.empty();
        ContentHeader header =
            new ContentHeader(
                HashIds.md5(canonicalUri.toString()),
                feedUrl.id(),
                sourceUri.toString(),
                fetchUri.toString(),
                canonicalUri.toString(),
                entry.getTitle(),
                toOffsetDateTime(entry.getPublishedDate()),
                feedText.orElse(null));
        if (contentHeaderRepository.insertOrRefreshFetchUrl(header)) {
          inserted++;
          if (feedUrl.fullTextMethod() == FullTextMethod.FEED) {
            feedText.ifPresent(fullText -> contentFullTextWriter.saveIfAbsent(header, fullText));
          }
        }
      }
      return inserted;
    } catch (IllegalArgumentException | IOException | InterruptedException | FeedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      logger.warn("Failed to import feed {}", feedUrl.url(), e);
      return 0;
    }
  }

  private OffsetDateTime toOffsetDateTime(Date date) {
    if (date == null) {
      return null;
    }
    return date.toInstant().atOffset(ZoneOffset.UTC);
  }
}
