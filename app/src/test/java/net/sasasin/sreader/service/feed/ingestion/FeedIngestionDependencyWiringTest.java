package net.sasasin.sreader.service.feed.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import net.sasasin.sreader.service.http.HttpFetchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class FeedIngestionDependencyWiringTest {

  @Autowired private ApplicationContext context;
  @Autowired private FeedEntryImportService service;
  @Autowired private FeedDocumentService feedDocumentService;
  @Autowired private FeedEntryImporter entryImporter;
  @Autowired private HttpFetchService httpFetchService;

  @Test
  void serviceUsesManagedDocumentServiceAndImporter() {
    assertThat(context.getBeansOfType(FeedDocumentService.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedEntryImporter.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedEntryImportService.class)).hasSize(1);

    assertThat(ReflectionTestUtils.getField(service, "feedDocumentService"))
        .isSameAs(feedDocumentService);
    assertThat(ReflectionTestUtils.getField(service, "entryImporter")).isSameAs(entryImporter);
    assertThat(ReflectionTestUtils.getField(entryImporter, "httpFetchService"))
        .isSameAs(httpFetchService);
    assertThat(ReflectionTestUtils.getField(feedDocumentService, "httpFetchService"))
        .isSameAs(httpFetchService);
  }
}
