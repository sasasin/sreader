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
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.junit.jupiter.api.Test;

class FullTextExtractionServiceTest {

  @Test
  void fetchesUsingFetchUrlAndUsesFinalResponseUriAsExtractorBaseUrl() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    URI finalUri = URI.create("https://final.test/article");
    when(http.get(URI.create(header.fetchUrl())))
        .thenReturn(new HttpFetchService.FetchedResource(finalUri, "<html>content</html>"));
    when(extractor.extract(
            eq(finalUri.toString()),
            eq("<html>content</html>"),
            org.mockito.ArgumentMatchers.any(
                net.sasasin.sreader.domain.ExtractionPlan.ExtractorKind.class)))
        .thenReturn("text");
    FullTextExtractionService service = service(http, extractor, mock(PlaywrightHtmlSource.class));

    assertThat(service.extract(header, FullTextMethod.HTTP_READABILITY)).isEqualTo("text");
    verify(http).get(URI.create(header.fetchUrl()));
    verify(http, never()).get(URI.create(header.sourceUrl()));
    verify(http, never()).get(URI.create(header.canonicalUrl()));
    verify(extractor)
        .extract(
            finalUri.toString(),
            "<html>content</html>",
            net.sasasin.sreader.domain.ExtractionPlan.ExtractorKind.READABILITY);
  }

  @Test
  void fetchesAndExtractsRenderedHtmlUsingFetchUrl() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    when(playwright.render(header.fetchUrl(), false))
        .thenReturn("<html><body><main>Rendered body</main></body></html>");
    when(rules.findBestRule(header.fetchUrl())).thenReturn(Optional.empty());
    FullTextExtractionService service = service(http, extractor, playwright);

    assertThat(service.extract(header, FullTextMethod.PLAYWRIGHT)).isEqualTo("Rendered body");
    verify(playwright).render(header.fetchUrl(), false);
    verify(playwright, never()).render(header.sourceUrl(), false);
    verify(playwright, never()).render(header.canonicalUrl(), false);
    verify(http, never()).get(any());
  }

  @Test
  void extractsFeedBodyWithoutNetworkAccess() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeader header =
        new ContentHeader(
            "id",
            "feed",
            "https://source.test/article",
            "https://fetch.test/article",
            "https://canonical.test/article",
            "title",
            null,
            "<p>Feed body</p>");

    assertThat(
            service(http, mock(HtmlTextExtractor.class), mock(PlaywrightHtmlSource.class))
                .extract(header, FullTextMethod.FEED))
        .isEqualTo("Feed body");
    verify(http, never()).get(any());
  }

  @Test
  void skipsDisabledPlaywrightPendingTarget() {
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentHeader header = header("https://source.test/article", "https://fetch.test/article");
    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(List.of(new PendingFullTextTarget(header, FullTextMethod.PLAYWRIGHT)));
    FullTextExtractionService service =
        new FullTextExtractionService(
            repository,
            mock(ContentFullTextWriter.class),
            mock(HtmlTextExtractor.class),
            mock(HttpFetchService.class),
            mock(PlaywrightHtmlSource.class),
            properties(false));

    assertThat(service.extractPending(10)).isZero();
  }

  private ContentHeader header(String sourceUrl, String fetchUrl) {
    return new ContentHeader(
        "id", "feed", sourceUrl, fetchUrl, "https://canonical.test/article", "title", null, null);
  }

  private FullTextExtractionService service(
      HttpFetchService http, HtmlTextExtractor extractor, PlaywrightHtmlSource playwright) {
    return new FullTextExtractionService(
        mock(ContentHeaderRepository.class),
        mock(ContentFullTextWriter.class),
        extractor,
        http,
        playwright,
        properties(true));
  }

  private FeedReaderProperties properties(boolean playwrightEnabled) {
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
