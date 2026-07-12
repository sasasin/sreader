package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeedEntryImportServiceTest {

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

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
    when(extractor.extract(any())).thenReturn(Optional.of("Feed body"));
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
    when(extractor.extract(any())).thenReturn(Optional.of("Feed body"));

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
    when(extractor.extract(any())).thenReturn(Optional.empty());

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

  @Test
  void skipsNullBlankAndWhitespaceLinksAndImportsValidOnly() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http,
            repository,
            mock(FeedEntryFullTextExtractor.class),
            mock(ContentFullTextWriter.class));
    // Atom entries without link elements yield null link; empty/whitespace use RSS description-only
    // items with explicit empty/whitespace link content via guide.
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Feed</title>
          <entry>
            <title>No link</title>
          </entry>
          <entry>
            <title>Empty link</title>
            <link href=""/>
          </entry>
          <entry>
            <title>Whitespace link</title>
            <link href="   "/>
          </entry>
          <entry>
            <title>Valid</title>
            <link href="https://example.test/valid"/>
          </entry>
        </feed>
        """;
    URI feedUri = URI.create("https://example.test/atom.xml");
    URI valid = URI.create("https://example.test/valid");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(valid)).thenReturn(valid);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    int inserted = service.importEntries(new FeedUrl("feed", "https://example.test/atom.xml"));

    assertThat(inserted).isEqualTo(1);
    verify(http, times(1)).resolveRedirect(any());
    verify(http).resolveRedirect(valid);
    verify(repository, times(1)).insertIfAbsent(any());
  }

  @Test
  void capturesHeaderFieldsIncludingRedirectedUrlAndPublishedDate() throws Exception {
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
        <item>
          <title>Article Title</title>
          <link>https://example.test/orig</link>
          <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
        </item>
        </channel></rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    URI original = URI.create("https://example.test/orig");
    URI redirected = URI.create("https://example.test/redirected");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(original)).thenReturn(redirected);
    when(repository.insertIfAbsent(any())).thenReturn(true);
    when(extractor.extract(any())).thenReturn(Optional.of("body from feed"));

    service.importEntries(
        new FeedUrl(
            "feed-id",
            "https://example.test/rss.xml",
            "active",
            null,
            null,
            null,
            FullTextMethod.FEED));

    ArgumentCaptor<ContentHeader> captor = ArgumentCaptor.forClass(ContentHeader.class);
    verify(repository).insertIfAbsent(captor.capture());
    ContentHeader header = captor.getValue();
    assertThat(header.id()).isEqualTo(HashIds.md5(redirected.toString()));
    assertThat(header.feedUrlId()).isEqualTo("feed-id");
    assertThat(header.url()).isEqualTo(redirected.toString());
    assertThat(header.title()).isEqualTo("Article Title");
    assertThat(header.feedText()).isEqualTo("body from feed");
    assertThat(header.publishedAt())
        .isEqualTo(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
  }

  @Test
  void publishedDateNullBecomesNullOnHeader() throws Exception {
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
        <item><title>No date</title><link>https://example.test/a</link></item>
        </channel></rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    URI articleUri = URI.create("https://example.test/a");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(articleUri)).thenReturn(articleUri);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    service.importEntries(new FeedUrl("feed", "https://example.test/rss.xml"));

    ArgumentCaptor<ContentHeader> captor = ArgumentCaptor.forClass(ContentHeader.class);
    verify(repository).insertIfAbsent(captor.capture());
    assertThat(captor.getValue().publishedAt()).isNull();
  }

  @Test
  void fullTextWriterFailureDoesNotChangeInsertCount() throws Exception {
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
        <item><title>Two</title><link>https://example.test/b</link></item>
        </channel></rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));
    when(http.resolveRedirect(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.insertIfAbsent(any())).thenReturn(true, false);
    when(extractor.extract(any())).thenReturn(Optional.of("text"));
    when(writer.saveIfAbsent(any(), any())).thenReturn(false);

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
    verify(writer, times(2)).saveIfAbsent(any(), any());
  }

  @Test
  void returnsZeroForInvalidFeedUrlWithoutCallingHttp() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http,
            repository,
            mock(FeedEntryFullTextExtractor.class),
            mock(ContentFullTextWriter.class));

    int inserted = service.importEntries(new FeedUrl("feed", "http://[invalid"));

    assertThat(inserted).isZero();
    verify(http, never()).get(any());
    verify(repository, never()).insertIfAbsent(any());
  }

  @Test
  void returnsZeroOnHttpIoExceptionWithoutCallingRepository() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http,
            repository,
            mock(FeedEntryFullTextExtractor.class),
            mock(ContentFullTextWriter.class));
    when(http.get(URI.create("https://example.test/rss.xml")))
        .thenThrow(new IOException("network"));

    int inserted = service.importEntries(new FeedUrl("feed", "https://example.test/rss.xml"));

    assertThat(inserted).isZero();
    verify(repository, never()).insertIfAbsent(any());
  }

  @Test
  void returnsZeroOnInterruptedExceptionAndRestoresInterruptFlag() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http,
            repository,
            mock(FeedEntryFullTextExtractor.class),
            mock(ContentFullTextWriter.class));
    when(http.get(URI.create("https://example.test/rss.xml")))
        .thenThrow(new InterruptedException("interrupted"));

    int inserted = service.importEntries(new FeedUrl("feed", "https://example.test/rss.xml"));

    assertThat(inserted).isZero();
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    verify(repository, never()).insertIfAbsent(any());
  }

  @Test
  void returnsZeroOnInvalidXmlWithoutCallingRepository() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http,
            repository,
            mock(FeedEntryFullTextExtractor.class),
            mock(ContentFullTextWriter.class));
    URI feedUri = URI.create("https://example.test/rss.xml");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, "not-a-feed"));

    int inserted = service.importEntries(new FeedUrl("feed", "https://example.test/rss.xml"));

    assertThat(inserted).isZero();
    verify(repository, never()).insertIfAbsent(any());
  }

  @Test
  void returnsZeroWhenResolveRedirectThrowsRuntimeIoFailureStyle() throws Exception {
    // resolveRedirect does not declare checked exceptions; production code catches IOException from
    // get/parse paths. Characterize IllegalArgumentException from URI.create on bad article link.
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
        <item><title>Bad</title><link>http://[invalid</link></item>
        <item><title>Good</title><link>https://example.test/good</link></item>
        </channel></rss>
        """;
    URI feedUri = URI.create("https://example.test/rss.xml");
    when(http.get(feedUri)).thenReturn(new HttpFetchService.FetchedResource(feedUri, xml));

    int inserted = service.importEntries(new FeedUrl("feed", "https://example.test/rss.xml"));

    assertThat(inserted).isZero();
    verify(repository, never()).insertIfAbsent(any());
    verify(http, never()).resolveRedirect(any());
  }
}
