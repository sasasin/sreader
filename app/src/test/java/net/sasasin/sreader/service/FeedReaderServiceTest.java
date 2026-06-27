package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.junit.jupiter.api.Test;

class FeedReaderServiceTest {

  @Test
  void runOnceCallsTextExportAfterFullTextExtraction() {
    FeedRegistrationService feedRegistrationService = mock(FeedRegistrationService.class);
    FeedUrlRepository feedUrlRepository = mock(FeedUrlRepository.class);
    FeedEntryImportService feedEntryImportService = mock(FeedEntryImportService.class);
    FullTextExtractionService fullTextExtractionService = mock(FullTextExtractionService.class);
    ContentTextFileExportService exportService = mock(ContentTextFileExportService.class);
    FeedReaderProperties properties =
        new FeedReaderProperties(
            null,
            null,
            null,
            null,
            new FeedReaderProperties.TextExport(true, Path.of("/tmp/sreader-test"), 7),
            List.of("https://example.test/rss.xml"));
    FeedUrl feedUrl = new FeedUrl("feed", "https://example.test/rss.xml");
    when(feedRegistrationService.registerFeedUrls(properties.seedFeedUrls())).thenReturn(1);
    when(feedUrlRepository.findActiveForReading()).thenReturn(List.of(feedUrl));
    when(feedEntryImportService.importEntries(feedUrl)).thenReturn(2);
    when(fullTextExtractionService.extractPending(100)).thenReturn(3);
    when(exportService.exportPending(7)).thenReturn(4);
    FeedReaderService service =
        new FeedReaderService(
            properties,
            feedRegistrationService,
            feedUrlRepository,
            feedEntryImportService,
            fullTextExtractionService,
            exportService);

    FeedReaderService.JobResult result = service.runOnce();

    assertThat(result.feedUrlsInserted()).isEqualTo(1);
    assertThat(result.contentHeadersInserted()).isEqualTo(2);
    assertThat(result.fullTextsInserted()).isEqualTo(3);
    assertThat(result.textFilesExported()).isEqualTo(4);
    verify(fullTextExtractionService).extractPending(100);
    verify(exportService).exportPending(7);
  }
}
