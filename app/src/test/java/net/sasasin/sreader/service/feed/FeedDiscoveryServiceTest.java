package net.sasasin.sreader.service.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.PlaywrightRenderMode;
import net.sasasin.sreader.service.extraction.browser.RenderedPage;
import org.junit.jupiter.api.Test;

class FeedDiscoveryServiceTest {

  @Test
  void discoversRssAndAtomLinksAndMakesAbsolute() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage(URI.create("https://example.com/"), PlaywrightRenderMode.STANDARD))
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
    when(pw.renderPage(URI.create("https://ex.com/s"), PlaywrightRenderMode.STANDARD))
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
    when(pw.renderPage(URI.create("https://ex.com/"), PlaywrightRenderMode.STANDARD))
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
    when(pw.renderPage(URI.create("https://ex.com/start"), PlaywrightRenderMode.STANDARD))
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

  @Test
  void usesRenderedFinalUriAsBaseForRelativeFeedLinks() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    URI input = URI.create("https://ex.com/start");
    when(pw.renderPage(input, PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(
                input,
                """
                <link rel="alternate" type="application/rss+xml" href="relative-feed.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    FeedDiscoveryService.DiscoveryResult res = svc.discoverWithResult(input);

    assertThat(res.inputUrl()).isEqualTo(input);
    assertThat(res.finalUrl()).isEqualTo(input);
    assertThat(res.feedUrls()).containsExactly(URI.create("https://ex.com/relative-feed.xml"));
  }

  @Test
  void discoverDelegatesToDiscoverWithResultFeedUrls() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage(URI.create("https://ex.com/"), PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(
                URI.create("https://ex.com/"),
                """
                <link rel="alternate" type="application/rss+xml" href="a.xml">
                <link rel="alternate" type="application/rss+xml" href="b.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    URI site = URI.create("https://ex.com/");
    assertThat(svc.discover(site)).isEqualTo(svc.discoverWithResult(site).feedUrls());
  }

  @Test
  void acceptsCaseWhitespaceAndJsonFeedTypes() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage(URI.create("https://ex.com/"), PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(
                URI.create("https://ex.com/"),
                """
                <link rel="alternate" type="  Application/RSS+XML  " href="rss.xml">
                <link rel="ALTERNATE" type="APPLICATION/ATOM+XML" href="atom.xml">
                <link rel="alternate" type="application/feed+json" href="feed.json">
                <link rel="stylesheet" type="application/rss+xml" href="style-not-alt.css">
                <link rel="alternate" type="text/html" href="page.html">
                <link rel="alternate" type="application/json" href="data.json">
                <link rel="alternate" type="" href="empty-type.xml">
                <link rel="alternate" type="   " href="blank-type.xml">
                <link rel="alternate" href="no-type.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    assertThat(svc.discover(URI.create("https://ex.com/")))
        .containsExactly(
            URI.create("https://ex.com/rss.xml"),
            URI.create("https://ex.com/atom.xml"),
            URI.create("https://ex.com/feed.json"));
  }

  @Test
  void skipsBlankHrefAndUsesFallbackResolveWhenAbsUrlBlank() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    // data: base yields blank absUrl for relative hrefs; raw href remains nonblank
    when(pw.renderPage(URI.create("https://ex.com/base/path"), PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(
                URI.create("data:text/html,base"),
                """
                <link rel="alternate" type="application/rss+xml" href="">
                <link rel="alternate" type="application/rss+xml" href="   ">
                <link rel="alternate" type="application/rss+xml" href="fallback-feed.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    List<URI> res = svc.discover(URI.create("https://ex.com/base/path"));

    assertThat(res).containsExactly(URI.create("https://ex.com/base/fallback-feed.xml"));
  }

  @Test
  void skipsWhenFallbackResolveThrows() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    // data: final URI makes absUrl blank; raw href "%zz" is nonblank but siteUrl.resolve throws.
    when(pw.renderPage(URI.create("https://ex.com/base"), PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(
                URI.create("data:text/html,base"),
                """
                <link rel="alternate" type="application/rss+xml" href="%zz">
                <link rel="alternate" type="application/rss+xml" href="https://ex.com/ok-feed.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    List<URI> res = svc.discover(URI.create("https://ex.com/base"));

    assertThat(res).containsExactly(URI.create("https://ex.com/ok-feed.xml"));
  }

  @Test
  void skipsInvalidAbsoluteUriAfterSeenAddAndContinues() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage(URI.create("https://ex.com/"), PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(
                URI.create("https://ex.com/"),
                // absUrl stays nonblank; URI.create rejects the illegal authority
                """
                <link rel="alternate" type="application/rss+xml" href="http://[invalid">
                <link rel="alternate" type="application/rss+xml" href="https://ex.com/good.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    List<URI> res = svc.discover(URI.create("https://ex.com/"));

    assertThat(res).containsExactly(URI.create("https://ex.com/good.xml"));
  }

  @Test
  void preservesInsertionOrderAndDeduplicatesResolvedUrls() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage(URI.create("https://ex.com/"), PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(
                URI.create("https://ex.com/"),
                """
                <link rel="alternate" type="application/rss+xml" href="/first.xml">
                <link rel="alternate" type="application/atom+xml" href="/second.xml">
                <link rel="alternate" type="application/rss+xml" href="https://ex.com/first.xml">
                <link rel="alternate" type="application/rss+xml" href="/third.xml">
                """));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    assertThat(svc.discover(URI.create("https://ex.com/")))
        .containsExactly(
            URI.create("https://ex.com/first.xml"),
            URI.create("https://ex.com/second.xml"),
            URI.create("https://ex.com/third.xml"));
  }

  @Test
  void emptyListWhenNoFeedLinks() {
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    when(pw.renderPage(URI.create("https://ex.com/"), PlaywrightRenderMode.STANDARD))
        .thenReturn(
            new RenderedPage(URI.create("https://ex.com/"), "<html><body>none</body></html>"));

    FeedDiscoveryService svc = new FeedDiscoveryService(pw);
    FeedDiscoveryService.DiscoveryResult res =
        svc.discoverWithResult(URI.create("https://ex.com/"));

    assertThat(res.inputUrl()).isEqualTo(URI.create("https://ex.com/"));
    assertThat(res.finalUrl()).isEqualTo(URI.create("https://ex.com/"));
    assertThat(res.feedUrls()).isEmpty();
  }
}
