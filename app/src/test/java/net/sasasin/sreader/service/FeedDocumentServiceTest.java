package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FeedDocumentServiceTest {

  private final HttpFetchService http = mock(HttpFetchService.class);
  private final FeedDocumentService service = new FeedDocumentService(http);
  private final URI feedUrl = URI.create("https://example.test/rss.xml");

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void fetchReturnsParsedSyndFeed() throws Exception {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>Example Feed</title>
          <item><title>One</title><link>https://example.test/a</link></item>
        </channel></rss>
        """;
    when(http.get(feedUrl)).thenReturn(new HttpFetchService.FetchedResource(feedUrl, xml));

    SyndFeed feed = service.fetch(feedUrl);

    assertThat(feed.getTitle()).isEqualTo("Example Feed");
    assertThat(feed.getEntries()).hasSize(1);
    assertThat(feed.getEntries().get(0).getTitle()).isEqualTo("One");
    assertThat(feed.getEntries().get(0).getLink()).isEqualTo("https://example.test/a");
  }

  @Test
  void fetchWrapsIoException() throws Exception {
    IOException cause = new IOException("network down");
    when(http.get(feedUrl)).thenThrow(cause);

    assertThatThrownBy(() -> service.fetch(feedUrl))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(feedUrl.toString())
        .hasCause(cause);
    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }

  @Test
  void fetchWrapsInterruptedExceptionAndRestoresInterruptFlag() throws Exception {
    InterruptedException cause = new InterruptedException("interrupted");
    when(http.get(feedUrl)).thenThrow(cause);

    assertThatThrownBy(() -> service.fetch(feedUrl))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(feedUrl.toString())
        .hasCause(cause);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void fetchWrapsFeedExceptionFromInvalidXml() throws Exception {
    when(http.get(feedUrl))
        .thenReturn(new HttpFetchService.FetchedResource(feedUrl, "not-xml-at-all"));

    assertThatThrownBy(() -> service.fetch(feedUrl))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(feedUrl.toString())
        .cause()
        .isInstanceOf(FeedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }
}
