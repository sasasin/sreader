package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import java.net.URI;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.ProbeResult;
import org.junit.jupiter.api.Test;

class FullTextProbeServiceTest {

  private FeedReaderProperties propsEnabled() {
    return new FeedReaderProperties(
        null,
        null,
        new FeedReaderProperties.Http(
            "ua", java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            true,
            true,
            10,
            10,
            java.time.Duration.ofSeconds(1),
            java.time.Duration.ofSeconds(1),
            null,
            null,
            1,
            1,
            java.time.Duration.ofMillis(10)),
        null,
        java.util.List.of());
  }

  private FeedReaderProperties propsDisabledPw() {
    return new FeedReaderProperties(
        null,
        null,
        new FeedReaderProperties.Http(
            "ua", java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            false,
            true,
            10,
            10,
            java.time.Duration.ofSeconds(1),
            java.time.Duration.ofSeconds(1),
            null,
            null,
            1,
            1,
            java.time.Duration.ofMillis(10)),
        null,
        java.util.List.of());
  }

  @Test
  void httpMethodUsesHttpFetchAndExtractor() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor ex = mock(HtmlTextExtractor.class);
    FeedDocumentService doc = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor fex = mock(FeedEntryFullTextExtractor.class);
    when(http.get(any(URI.class)))
        .thenReturn(
            new HttpFetchService.FetchedResource(URI.create("https://final/"), "<html>ok</html>"));
    when(ex.extract(any(), any(), any(), any())).thenReturn("extracted");

    FullTextProbeService svc =
        new FullTextProbeService(http, pw, ex, doc, picker, fex, propsEnabled());

    ProbeResult r =
        svc.probeArticle(URI.create("https://in/"), FullTextMethod.HTTP, Optional.empty());

    assertThat(r.text()).isEqualTo("extracted");
    assertThat(r.finalUrl().toString()).isEqualTo("https://final/");
    verify(pw, never()).renderPage(any(), anyBoolean());
  }

  @Test
  void httpReadabilityUsesHttpFetchAndReadabilityExtractor() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor ex = mock(HtmlTextExtractor.class);
    URI finalUri = URI.create("https://final/");
    when(http.get(URI.create("https://in/")))
        .thenReturn(new HttpFetchService.FetchedResource(finalUri, "<html>ok</html>"));
    when(ex.extract(any(), any(), any(), any())).thenReturn("extracted");
    FullTextProbeService svc =
        new FullTextProbeService(
            http,
            pw,
            ex,
            mock(FeedDocumentService.class),
            mock(FeedEntryPicker.class),
            mock(FeedEntryFullTextExtractor.class),
            propsDisabledPw());

    ProbeResult result =
        svc.probeArticle(
            URI.create("https://in/"), FullTextMethod.HTTP_READABILITY, Optional.empty());

    assertThat(result.text()).isEqualTo("extracted");
    assertThat(result.finalUrl()).isEqualTo(finalUri);
    verify(ex)
        .extract(
            eq(finalUri.toString()),
            eq("<html>ok</html>"),
            eq(net.sasasin.sreader.domain.ExtractionPlan.ExtractorKind.READABILITY),
            eq(Optional.empty()));
    verify(pw, never()).renderPage(any(), anyBoolean());
  }

  @Test
  void playwrightMethodUsesRenderPage() {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor ex = mock(HtmlTextExtractor.class);
    FeedDocumentService doc = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor fex = mock(FeedEntryFullTextExtractor.class);
    when(pw.renderPage(any(), anyBoolean()))
        .thenReturn(new RenderedPage(URI.create("https://f/"), "<h>pw</h>"));
    when(ex.extract(any(), any(), any(), any())).thenReturn("pwtext");

    FullTextProbeService svc =
        new FullTextProbeService(http, pw, ex, doc, picker, fex, propsEnabled());

    ProbeResult r =
        svc.probeArticle(
            URI.create("https://in/"), FullTextMethod.PLAYWRIGHT_READABILITY, Optional.of("//h"));

    assertThat(r.text()).isEqualTo("pwtext");
    verify(pw).renderPage(any(), anyBoolean());
  }

  @Test
  void feedMethodOnProbeFeedUsesFeedExtractorNoHttp() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor ex = mock(HtmlTextExtractor.class);
    FeedDocumentService doc = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor fex = mock(FeedEntryFullTextExtractor.class);

    SyndFeed synd = mock(SyndFeed.class);
    SyndEntry entry = mock(SyndEntry.class);
    when(entry.getLink()).thenReturn("https://art/");
    when(entry.getTitle()).thenReturn("title");
    when(picker.pick(any(), any(), anyBoolean())).thenReturn(Optional.of(entry));
    when(doc.fetch(any())).thenReturn(synd);
    when(fex.extract(entry)).thenReturn(Optional.of("feed body text"));

    FullTextProbeService svc =
        new FullTextProbeService(http, pw, ex, doc, picker, fex, propsEnabled());

    ProbeResult r =
        svc.probeFeed(
            URI.create("https://f/"),
            FullTextMethod.FEED,
            FeedEntrySelection.first(),
            Optional.empty());

    assertThat(r.text()).isEqualTo("feed body text");
    verify(picker).pick(any(), any(), eq(false));
    verify(http, never()).get(any());
    verify(ex, never()).extract(any(), any(), any(), any());
  }

  @Test
  void nonFeedProbeFeedDelegatesToArticlePath() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor ex = mock(HtmlTextExtractor.class);
    FeedDocumentService doc = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor fex = mock(FeedEntryFullTextExtractor.class);

    SyndFeed synd = mock(SyndFeed.class);
    SyndEntry entry = mock(SyndEntry.class);
    when(entry.getLink()).thenReturn("https://art/");
    when(entry.getTitle()).thenReturn("et");
    when(picker.pick(any(), any(), anyBoolean())).thenReturn(Optional.of(entry));
    when(doc.fetch(any())).thenReturn(synd);
    when(http.resolveRedirect(any(URI.class))).thenReturn(URI.create("https://art/"));
    when(http.get(any(URI.class)))
        .thenReturn(
            new HttpFetchService.FetchedResource(URI.create("https://final-art/"), "<a>body</a>"));
    when(ex.extract(any(), any(), any(), any())).thenReturn("art body");

    FullTextProbeService svc =
        new FullTextProbeService(http, pw, ex, doc, picker, fex, propsEnabled());

    ProbeResult r =
        svc.probeFeed(
            URI.create("https://f/"),
            FullTextMethod.HTTP,
            FeedEntrySelection.first(),
            Optional.empty());

    assertThat(r.text()).isEqualTo("art body");
    verify(http).get(any(URI.class));
  }

  @Test
  void disabledPlaywrightThrows() {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor ex = mock(HtmlTextExtractor.class);
    FeedDocumentService doc = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor fex = mock(FeedEntryFullTextExtractor.class);

    FullTextProbeService svc =
        new FullTextProbeService(http, pw, ex, doc, picker, fex, propsDisabledPw());

    assertThatThrownBy(
            () ->
                svc.probeArticle(
                    URI.create("https://x/"), FullTextMethod.PLAYWRIGHT, Optional.empty()))
        .isInstanceOf(FullTextProbeService.PlaywrightDisabledException.class)
        .hasMessageContaining("Playwright");
  }

  @Test
  void playwrightMisconfigurationIsMappedToPlaywrightDisabledException() {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource pw = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor ex = mock(HtmlTextExtractor.class);
    FeedDocumentService doc = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor fex = mock(FeedEntryFullTextExtractor.class);
    when(pw.renderPage(any(), anyBoolean()))
        .thenThrow(new IllegalStateException("Infy Scroll extension directory is not configured"));

    FullTextProbeService svc =
        new FullTextProbeService(http, pw, ex, doc, picker, fex, propsEnabled());

    assertThatThrownBy(
            () ->
                svc.probeArticle(
                    URI.create("https://x/"),
                    FullTextMethod.PLAYWRIGHT_INFY_SCROLL,
                    Optional.empty()))
        .isInstanceOf(FullTextProbeService.PlaywrightDisabledException.class)
        .hasMessageContaining("Infy Scroll extension directory");
  }
}
