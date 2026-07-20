package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import net.sasasin.sreader.service.canonicalization.ContentCanonicalizationMaintenanceService;
import net.sasasin.sreader.service.extraction.FullTextExtractionService;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.feed.FeedDiscoveryService;
import net.sasasin.sreader.service.feed.FeedRegistrationService;
import net.sasasin.sreader.service.feed.toml.FeedTomlService;
import net.sasasin.sreader.service.job.FeedReaderService;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import net.sasasin.sreader.service.text.ContentTextFileExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Smoke-checks that feature-package service facades remain Spring-visible after the package split.
 */
@SpringBootTest
class ServiceFacadeWiringTest {

  @Autowired private ApplicationContext context;

  @Test
  void featureFacadesAreRegistered() {
    assertThat(context.getBean(FeedReaderService.class)).isNotNull();
    assertThat(context.getBean(FeedTomlService.class)).isNotNull();
    assertThat(context.getBean(FullTextExtractionService.class)).isNotNull();
    assertThat(context.getBean(PlaywrightHtmlSource.class)).isNotNull();
    assertThat(context.getBean(FullTextProbeService.class)).isNotNull();
    assertThat(context.getBean(ContentCanonicalizationMaintenanceService.class)).isNotNull();
    assertThat(context.getBean(ContentTextFileExportService.class)).isNotNull();
    assertThat(context.getBean(FeedDiscoveryService.class)).isNotNull();
    assertThat(context.getBean(FeedRegistrationService.class)).isNotNull();
  }
}
