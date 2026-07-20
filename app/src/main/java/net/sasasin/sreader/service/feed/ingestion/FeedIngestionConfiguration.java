package net.sasasin.sreader.service.feed.ingestion;

import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.article.ArticleUrlCanonicalizer;
import net.sasasin.sreader.service.extraction.ContentFullTextWriter;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.http.HttpFetchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition root for feed entry import collaborators. */
@Configuration(proxyBeanMethods = false)
class FeedIngestionConfiguration {

  @Bean
  FeedEntryImporter feedEntryImporter(
      HttpFetchService httpFetchService,
      ArticleUrlCanonicalizer canonicalizer,
      ContentHeaderRepository headers,
      FeedEntryFullTextExtractor feedTextExtractor,
      ContentFullTextWriter fullTextWriter) {
    return new FeedEntryImporter(
        httpFetchService, canonicalizer, headers, feedTextExtractor, fullTextWriter);
  }
}
