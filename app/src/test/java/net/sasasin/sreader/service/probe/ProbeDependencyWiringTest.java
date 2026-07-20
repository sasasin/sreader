package net.sasasin.sreader.service.probe;

import static org.assertj.core.api.Assertions.assertThat;

import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.http.HttpFetchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ProbeDependencyWiringTest {

  @Autowired private ApplicationContext context;
  @Autowired private FullTextProbeService service;
  @Autowired private ProbeDocumentFetcher documentFetcher;
  @Autowired private HttpFetchService httpFetchService;
  @Autowired private PlaywrightHtmlSource playwrightHtmlSource;
  @Autowired private FeedReaderProperties properties;

  @Test
  void serviceUsesManagedFetcherWithoutHoldingPlaywrightDirectly() {
    assertThat(context.getBeansOfType(ProbeDocumentFetcher.class)).hasSize(1);
    assertThat(context.getBeansOfType(FullTextProbeService.class)).hasSize(1);

    assertThat(ReflectionTestUtils.getField(service, "documentFetcher")).isSameAs(documentFetcher);
    assertThat(ReflectionTestUtils.getField(documentFetcher, "httpFetchService"))
        .isSameAs(httpFetchService);
    assertThat(ReflectionTestUtils.getField(documentFetcher, "playwrightHtmlSource"))
        .isSameAs(playwrightHtmlSource);
    assertThat(ReflectionTestUtils.getField(documentFetcher, "properties")).isSameAs(properties);
    assertThat(FullTextProbeService.class.getDeclaredFields())
        .extracting(java.lang.reflect.Field::getName)
        .doesNotContain("playwrightHtmlSource", "properties");
  }
}
