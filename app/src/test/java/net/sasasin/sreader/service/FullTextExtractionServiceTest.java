package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.junit.jupiter.api.Test;

class FullTextExtractionServiceTest {

  @Test
  void extractsTextWithXpathRule() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    FullTextExtractionService service = service(mock(ContentHeaderRepository.class), rules, http);
    ContentHeader header =
        new ContentHeader("id", "feed", "https://example.test/articles/1", "title", null);
    when(http.get(URI.create(header.url())))
        .thenReturn(
            new HttpFetchService.FetchedResource(
                URI.create(header.url()),
                "<html><body><article><h1>Hello</h1><p>World</p></article><nav>skip</nav></body></html>"));
    when(rules.findBestRule(header.url()))
        .thenReturn(
            Optional.of(new ExtractRule("rule", "https://example.test/articles/", "//article")));

    assertThat(service.extract(header)).isEqualTo("Hello World");
  }

  @Test
  void fallsBackToBodyTextWhenNoRuleMatches() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    FullTextExtractionService service = service(mock(ContentHeaderRepository.class), rules, http);
    ContentHeader header =
        new ContentHeader("id", "feed", "https://example.test/no-rule", "title", null);
    when(http.get(URI.create(header.url())))
        .thenReturn(
            new HttpFetchService.FetchedResource(
                URI.create(header.url()), "<html><body><main>Fallback body</main></body></html>"));
    when(rules.findBestRule(header.url())).thenReturn(Optional.empty());

    assertThat(service.extract(header)).isEqualTo("Fallback body");
  }

  @Test
  void httpReadabilityUsesHttpFetchAndReadabilityExtractor() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    ContentHeader header =
        new ContentHeader("id", "feed", "https://example.test/articles/1", "title", null);
    URI finalUri = URI.create("https://example.test/articles/final");
    when(http.get(URI.create(header.url())))
        .thenReturn(new HttpFetchService.FetchedResource(finalUri, "<html>content</html>"));
    when(extractor.extract(eq(finalUri.toString()), eq("<html>content</html>"), any()))
        .thenReturn("readability text");
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextWriter.class),
            extractor,
            http,
            playwright,
            testProperties(false));

    assertThat(service.extract(header, FullTextMethod.HTTP_READABILITY))
        .isEqualTo("readability text");
    verify(http).get(URI.create(header.url()));
    verify(extractor)
        .extract(
            finalUri.toString(),
            "<html>content</html>",
            net.sasasin.sreader.domain.ExtractionPlan.ExtractorKind.READABILITY);
    verify(playwright, never()).render(any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void extractsHttpAndFeedPendingTargetsAndSkipsDisabledPlaywrightMethods() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            new HtmlTextExtractor(rules),
            http,
            playwright,
            testProperties(false));
    ContentHeader httpHeader =
        new ContentHeader("http", "feed", "https://example.test/http", "HTTP", null);
    ContentHeader feedHeader =
        new ContentHeader(
            "feed", "feed", "https://example.test/feed", "FEED", null, "<p>Feed body</p>");
    ContentHeader playwrightHeader =
        new ContentHeader(
            "playwright", "feed", "https://example.test/playwright", "PLAYWRIGHT", null);

    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(httpHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(feedHeader, FullTextMethod.FEED),
                new PendingFullTextTarget(playwrightHeader, FullTextMethod.PLAYWRIGHT)));
    when(http.get(URI.create(httpHeader.url())))
        .thenReturn(
            new HttpFetchService.FetchedResource(
                URI.create(httpHeader.url()), "<html><body>HTTP body</body></html>"));
    when(rules.findBestRule(httpHeader.url())).thenReturn(Optional.empty());
    when(writer.saveIfAbsent(httpHeader, "HTTP body")).thenReturn(true);
    when(writer.saveIfAbsent(feedHeader, "Feed body")).thenReturn(true);

    assertThat(service.extractPending(10)).isEqualTo(2);
    verify(http).get(URI.create(httpHeader.url()));
    verify(http, never()).get(URI.create(playwrightHeader.url()));
    verify(writer).saveIfAbsent(httpHeader, "HTTP body");
    verify(writer).saveIfAbsent(feedHeader, "Feed body");
    verify(playwright, never()).render(any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void extractsRenderedHtmlWithPlaywrightWhenEnabled() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            writer,
            new HtmlTextExtractor(rules),
            http,
            playwright,
            testProperties(true));
    ContentHeader header =
        new ContentHeader("playwright", "feed", "https://example.test/js", "JS", null);
    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(List.of(new PendingFullTextTarget(header, FullTextMethod.PLAYWRIGHT)));
    when(playwright.render(header.url(), false))
        .thenReturn("<html><body><main>Rendered body</main></body></html>");
    when(rules.findBestRule(header.url())).thenReturn(Optional.empty());
    when(writer.saveIfAbsent(header, "Rendered body")).thenReturn(true);

    assertThat(service.extractPending(10)).isEqualTo(1);
    verify(playwright).render(header.url(), false);
    verify(http, never()).get(any());
  }

  private FullTextExtractionService service(
      ContentHeaderRepository repository, ExtractRuleService rules, HttpFetchService http) {
    return new FullTextExtractionService(
        repository,
        mock(ContentFullTextWriter.class),
        new HtmlTextExtractor(rules),
        http,
        mock(PlaywrightHtmlSource.class),
        testProperties(false));
  }

  private FeedReaderProperties testProperties(boolean playwrightEnabled) {
    return new FeedReaderProperties(
        new FeedReaderProperties.Scheduler(false, "0 */15 * * * *"),
        new FeedReaderProperties.Job(false),
        new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            playwrightEnabled,
            true,
            1280,
            1600,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            null,
            null,
            1,
            1,
            Duration.ofMillis(1)),
        null,
        List.of());
  }
}
