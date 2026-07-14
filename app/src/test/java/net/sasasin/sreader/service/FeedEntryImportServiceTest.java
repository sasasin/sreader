package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
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
  void usesCanonicalUrlForIdAndRefreshesOnlyTheFetchUrlForExistingArticle() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service = service(http, repository);
    URI feed = URI.create("https://example.test/rss.xml");
    URI source = URI.create("https://example.test/article");
    String xml = rss(source.toString());
    URI firstFetch = URI.create("https://publisher.example.test/articles/article?gs=first");
    URI secondFetch = URI.create("https://publisher.example.test/articles/article?gs=second");
    when(http.get(feed)).thenReturn(new HttpFetchService.FetchedResource(feed, xml));
    when(http.resolveRedirect(source)).thenReturn(firstFetch, secondFetch);
    when(repository.insertOrRefreshFetchUrl(any())).thenReturn(true, false);

    assertThat(service.importEntries(new FeedUrl("feed", feed.toString()))).isEqualTo(1);
    assertThat(service.importEntries(new FeedUrl("feed", feed.toString()))).isZero();

    ArgumentCaptor<ContentHeader> headers = ArgumentCaptor.forClass(ContentHeader.class);
    verify(repository, org.mockito.Mockito.times(2)).insertOrRefreshFetchUrl(headers.capture());
    assertThat(headers.getAllValues())
        .allSatisfy(
            header -> {
              assertThat(header.sourceUrl()).isEqualTo(source.toString());
              assertThat(header.canonicalUrl())
                  .isEqualTo("https://publisher.example.test/articles/article");
              assertThat(header.id()).isEqualTo(HashIds.md5(header.canonicalUrl()));
            });
    assertThat(headers.getAllValues().get(0).fetchUrl()).isEqualTo(firstFetch.toString());
    assertThat(headers.getAllValues().get(1).fetchUrl()).isEqualTo(secondFetch.toString());
  }

  @Test
  void savesFeedBodyOnlyForNewHeader() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http, new ArticleUrlCanonicalizer(), repository, extractor, writer);
    URI feed = URI.create("https://example.test/rss.xml");
    URI article = URI.create("https://example.test/article");
    when(http.get(feed))
        .thenReturn(new HttpFetchService.FetchedResource(feed, rss(article.toString())));
    when(http.resolveRedirect(article)).thenReturn(article);
    when(repository.insertOrRefreshFetchUrl(any())).thenReturn(true, false);
    when(extractor.extract(any())).thenReturn(Optional.of("Feed body"));

    FeedUrl feedUrl =
        new FeedUrl("feed", feed.toString(), "active", null, null, null, FullTextMethod.FEED);
    assertThat(service.importEntries(feedUrl)).isEqualTo(1);
    assertThat(service.importEntries(feedUrl)).isZero();
    verify(writer)
        .saveIfAbsent(any(ContentHeader.class), org.mockito.ArgumentMatchers.eq("Feed body"));
  }

  @Test
  void returnsZeroWithoutRepositoryCallWhenFeedFetchFails() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    URI feed = URI.create("https://example.test/rss.xml");
    when(http.get(feed)).thenThrow(new IOException("network"));

    assertThat(service(http, repository).importEntries(new FeedUrl("feed", feed.toString())))
        .isZero();
    verify(repository, never()).insertOrRefreshFetchUrl(any());
  }

  private FeedEntryImportService service(
      HttpFetchService http, ContentHeaderRepository repository) {
    return new FeedEntryImportService(
        http,
        new ArticleUrlCanonicalizer("publisher.example.test", "/articles/"),
        repository,
        mock(FeedEntryFullTextExtractor.class),
        mock(ContentFullTextWriter.class));
  }

  private String rss(String link) {
    return "<rss version=\"2.0\"><channel><title>Feed</title><item><title>Article</title><link>"
        + link
        + "</link></item></channel></rss>";
  }
}
