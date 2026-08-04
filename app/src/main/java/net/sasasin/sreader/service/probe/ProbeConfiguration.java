package net.sasasin.sreader.service.probe;

import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeEngine;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleCatalog;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition root for full-text probe collaborators. */
@Configuration(proxyBeanMethods = false)
class ProbeConfiguration {

  @Bean
  ProbeDocumentFetcher probeDocumentFetcher(
      HttpFetchService httpFetchService,
      HttpArticlePageSessionFactory httpArticlePageSessionFactory,
      PlaywrightHtmlSource playwrightHtmlSource,
      FeedReaderProperties properties,
      AutoPagerizeRuleCatalog autoPagerizeRuleCatalog,
      AutoPagerizeEngine autoPagerizeEngine) {
    return new ProbeDocumentFetcher(
        httpFetchService,
        httpArticlePageSessionFactory,
        playwrightHtmlSource,
        properties,
        autoPagerizeRuleCatalog,
        autoPagerizeEngine);
  }
}
