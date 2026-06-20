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

  public SyndFeed fetch(URI feedUrl) {
    try {
      HttpFetchService.FetchedResource res = httpFetchService.get(feedUrl);
      return new SyndFeedInput()
          .build(
              new XmlReader(new ByteArrayInputStream(res.body().getBytes(StandardCharsets.UTF_8))));
    } catch (IOException | InterruptedException | FeedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new RuntimeException("Failed to fetch or parse feed: " + feedUrl, e);
    }
  }
}
