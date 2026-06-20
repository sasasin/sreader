package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.junit.jupiter.api.Test;

class FeedEntryImportServiceTest {

  @Test
  void importsFeedEntriesAndSuppressesDuplicateArticleUrls() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service = new FeedEntryImportService(http, repository);
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Feed</title>
        <item><title>One</title><link>https://example.test/a</link></item>
        <item><title>Two</title><link>https://example.test/b</link></item>
        </channel></rss>
        """;
    when(http.get(URI.create("https://example.test/rss.xml")))
        .thenReturn(
            new HttpFetchService.FetchedResource(URI.create("https://example.test/rss.xml"), xml));
    when(http.resolveRedirect(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.insertIfAbsent(any())).thenReturn(true, false);

    int inserted = service.importEntries(new FeedUrl("feed", "https://example.test/rss.xml"));

    assertThat(inserted).isEqualTo(1);
  }
}
