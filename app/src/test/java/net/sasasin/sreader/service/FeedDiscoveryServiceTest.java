package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedDiscoveryServiceTest {

  @Test
  void discoversRssAndAtomLinksAndMakesAbsolute() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage("https://example.com/", false))
        .thenReturn(
            new RenderedPage(
                URI.create("https://example.com/"),
                """
                <html><head>
                <link rel="alternate" type="application/rss+xml" href="/rss.xml">
                <link rel="alternate" type="application/atom+xml" href="https://example.com/atom.xml">
                <link rel="alternate" type="text/html" href="/page">
                </head></html>
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    List<URI> res = svc.discover(URI.create("https://example.com/"));

    assertThat(res)
        .containsExactly(
            URI.create("https://example.com/rss.xml"), URI.create("https://example.com/atom.xml"));
  }

  @Test
  void removesDuplicatesAndSkipsNonFeed() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage("https://ex.com/s", false))
        .thenReturn(
            new RenderedPage(
                URI.create("https://ex.com/s"),
                """
                <html>
                <link rel="alternate" type="application/rss+xml" href="feed1.xml">
                <link rel="alternate" type="application/rss+xml" href="feed1.xml">
                <link rel="alternate" type="application/json" href="notfeed">
                </html>
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    List<URI> res = svc.discover(URI.create("https://ex.com/s"));
    assertThat(res).containsExactly(URI.create("https://ex.com/feed1.xml"));
  }

  @Test
  void acceptsAdditionalFeedTypes() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage("https://ex.com/", false))
        .thenReturn(
            new RenderedPage(
                URI.create("https://ex.com/"),
                """
                <link rel="alternate" type="application/xml" href="a.xml">
                <link rel="alternate" type="text/xml" href="b.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    List<URI> res = svc.discover(URI.create("https://ex.com/"));
    assertThat(res).hasSize(2);
  }

  @Test
  void exposesFinalUrlForVerboseDiagnostics() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage("https://ex.com/start", false))
        .thenReturn(
            new RenderedPage(
                URI.create("https://ex.com/final"),
                """
                <link rel="alternate" type="application/rss+xml" href="feed.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    FeedDiscoveryService.DiscoveryResult res =
        svc.discoverWithResult(URI.create("https://ex.com/start"));

    assertThat(res.finalUrl()).isEqualTo(URI.create("https://ex.com/final"));
    assertThat(res.feedUrls()).containsExactly(URI.create("https://ex.com/feed.xml"));
  }
}
