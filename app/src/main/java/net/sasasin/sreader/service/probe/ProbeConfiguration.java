package net.sasasin.sreader.service.probe;

import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.http.HttpFetchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition root for full-text probe collaborators. */
@Configuration(proxyBeanMethods = false)
class ProbeConfiguration {

  @Bean
  ProbeDocumentFetcher probeDocumentFetcher(
      HttpFetchService httpFetchService,
      PlaywrightHtmlSource playwrightHtmlSource,
      FeedReaderProperties properties) {
    return new ProbeDocumentFetcher(httpFetchService, playwrightHtmlSource, properties);
  }
}
