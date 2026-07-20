package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    FeedDocumentOutcome.Fetched fetched = (FeedDocumentOutcome.Fetched) service.fetch(feedUrl);

    assertThat(fetched.feed().getTitle()).isEqualTo("Example Feed");
    assertThat(fetched.entries()).hasSize(1);
    assertThat(fetched.entries().get(0).getTitle()).isEqualTo("One");
    assertThat(fetched.entries().get(0).getLink()).isEqualTo("https://example.test/a");
  }

  @Test
  void fetchEmptyFeedIsFetchedEmpty() throws Exception {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Empty</title></channel></rss>
        """;
    when(http.get(feedUrl)).thenReturn(new HttpFetchService.FetchedResource(feedUrl, xml));

    FeedDocumentOutcome.Fetched fetched = (FeedDocumentOutcome.Fetched) service.fetch(feedUrl);
    assertThat(fetched.entries()).isEmpty();
  }

  @Test
  void fetchIoFailureIsFailedFetchFeed() throws Exception {
    IOException cause = new IOException("network down");
    when(http.get(feedUrl)).thenThrow(cause);

    FeedDocumentOutcome.Failed failed = (FeedDocumentOutcome.Failed) service.fetch(feedUrl);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_FEED);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.IO);
    assertThat(failed.failure().cause()).contains(cause);
    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }

  @Test
  void fetchHttpStatusFailureIsFailedWithHttpStatusKind() throws Exception {
    IOException cause = new IOException("GET " + feedUrl + " returned HTTP 503");
    when(http.get(feedUrl)).thenThrow(cause);

    FeedDocumentOutcome.Failed failed = (FeedDocumentOutcome.Failed) service.fetch(feedUrl);

    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_FEED);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.HTTP_STATUS);
    assertThat(failed.failure().cause()).contains(cause);
  }

  @Test
  void fetchInterruptedIsFailedAndRestoresInterruptFlag() throws Exception {
    InterruptedException cause = new InterruptedException("interrupted");
    when(http.get(feedUrl)).thenThrow(cause);

    FeedDocumentOutcome.Failed failed = (FeedDocumentOutcome.Failed) service.fetch(feedUrl);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.INTERRUPTED);
    assertThat(failed.failure().cause()).contains(cause);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void fetchInvalidXmlIsFailedParseFeed() throws Exception {
    when(http.get(feedUrl))
        .thenReturn(new HttpFetchService.FetchedResource(feedUrl, "not-xml-at-all"));

    FeedDocumentOutcome.Failed failed = (FeedDocumentOutcome.Failed) service.fetch(feedUrl);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.PARSE_FEED);
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.PARSE);
    assertThat(failed.failure().cause()).isPresent();
    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }
}
