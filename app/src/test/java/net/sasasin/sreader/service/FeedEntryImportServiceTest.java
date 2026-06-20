package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.junit.jupiter.api.Test;

class FeedEntryImportServiceTest {

  @Test
  void importsFeedEntriesAndSuppressesDuplicateArticleUrls() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http,
            repository,
            mock(FeedEntryFullTextExtractor.class),
            mock(ContentFullTextWriter.class));
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

  @Test
  void savesFeedFullTextDuringImportWithoutFetchingArticleUrl() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FeedEntryImportService service =
        new FeedEntryImportService(http, repository, extractor, writer);
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"
             xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel><title>Feed</title>
            <item>
              <title>One</title>
              <link>https://example.test/a</link>
              <content:encoded><![CDATA[<article><p>Feed body</p></article>]]></content:encoded>
            </item>
          </channel>
        </rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    URI articleUri = URI.create("https://example.test/a");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(articleUri)).thenReturn(articleUri);
    when(repository.insertIfAbsent(any())).thenReturn(true);
    when(extractor.extract(any())).thenReturn(java.util.Optional.of("Feed body"));
    when(writer.saveIfAbsent(any(), any())).thenReturn(true);

    int inserted =
        service.importEntries(
            new FeedUrl(
                "feed",
                "https://example.test/rss.xml",
                "active",
                null,
                null,
                null,
                FullTextMethod.FEED));

    assertThat(inserted).isEqualTo(1);
    verify(http).get(feedUri);
    verify(http, never()).get(articleUri);
    verify(writer)
        .saveIfAbsent(
            argThat(header -> header.url().equals("https://example.test/a")),
            org.mockito.ArgumentMatchers.eq("Feed body"));
  }

  @Test
  void savesFeedFullTextEvenWhenHeaderAlreadyExists() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FeedEntryImportService service =
        new FeedEntryImportService(http, repository, extractor, writer);
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Feed</title>
        <item><title>One</title><link>https://example.test/a</link><description>Feed body</description></item>
        </channel></rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    URI articleUri = URI.create("https://example.test/a");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(articleUri)).thenReturn(articleUri);
    when(repository.insertIfAbsent(any())).thenReturn(false);
    when(extractor.extract(any())).thenReturn(java.util.Optional.of("Feed body"));

    int inserted =
        service.importEntries(
            new FeedUrl(
                "feed",
                "https://example.test/rss.xml",
                "active",
                null,
                null,
                null,
                FullTextMethod.FEED));

    assertThat(inserted).isZero();
    verify(writer)
        .saveIfAbsent(any(ContentHeader.class), org.mockito.ArgumentMatchers.eq("Feed body"));
  }

  @Test
  void doesNotSaveFeedFullTextWhenEntryHasNoTextCandidate() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FeedEntryImportService service =
        new FeedEntryImportService(http, repository, extractor, writer);
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Feed</title>
        <item><title>One</title><link>https://example.test/a</link></item>
        </channel></rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    URI articleUri = URI.create("https://example.test/a");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(articleUri)).thenReturn(articleUri);
    when(repository.insertIfAbsent(any())).thenReturn(true);
    when(extractor.extract(any())).thenReturn(java.util.Optional.empty());

    int inserted =
        service.importEntries(
            new FeedUrl(
                "feed",
                "https://example.test/rss.xml",
                "active",
                null,
                null,
                null,
                FullTextMethod.FEED));

    assertThat(inserted).isEqualTo(1);
    verify(writer, never()).saveIfAbsent(any(), any());
  }

  @Test
  void doesNotSaveFullTextDuringImportForHttpMethod() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FeedEntryImportService service =
        new FeedEntryImportService(http, repository, extractor, writer);
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Feed</title>
        <item><title>One</title><link>https://example.test/a</link></item>
        </channel></rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    URI articleUri = URI.create("https://example.test/a");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(articleUri)).thenReturn(articleUri);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    int inserted = service.importEntries(new FeedUrl("feed", "https://example.test/rss.xml"));

    assertThat(inserted).isEqualTo(1);
    verify(extractor, never()).extract(any());
    verify(writer, never()).saveIfAbsent(any(), any());
  }
}
