package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class FeedDocumentService {

  private final HttpFetchService httpFetchService;

  public FeedDocumentService(HttpFetchService httpFetchService) {
    this.httpFetchService = httpFetchService;
  }

  public FeedDocumentOutcome fetch(URI feedUrl) {
    try {
      HttpFetchService.FetchedResource res = httpFetchService.get(feedUrl);
      SyndFeed feed =
          new SyndFeedInput()
              .build(
                  new XmlReader(
                      new ByteArrayInputStream(res.body().getBytes(StandardCharsets.UTF_8))));
      return new FeedDocumentOutcome.Fetched(feed);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new FeedDocumentOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_FEED,
              FailureKind.INTERRUPTED,
              feedUrl.toString(),
              "Feed fetch interrupted for " + feedUrl,
              e));
    } catch (IOException e) {
      return new FeedDocumentOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_FEED,
              FailureKind.IO,
              feedUrl.toString(),
              "Failed to fetch feed: " + feedUrl + ": " + e.getMessage(),
              e));
    } catch (FeedException e) {
      return new FeedDocumentOutcome.Failed(
          OperationFailure.of(
              FailureStage.PARSE_FEED,
              FailureKind.PARSE,
              feedUrl.toString(),
              "Failed to parse feed: " + feedUrl + ": " + e.getMessage(),
              e));
    }
  }
}
