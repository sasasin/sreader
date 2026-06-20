package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
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
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class), mock(ContentFullTextWriter.class), rules, http);
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
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class), mock(ContentFullTextWriter.class), rules, http);
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
  void extractsOnlyHttpPendingTargetsAndSkipsUnsupportedMethods() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FullTextExtractionService service =
        new FullTextExtractionService(repository, writer, rules, http);
    ContentHeader httpHeader =
        new ContentHeader("http", "feed", "https://example.test/http", "HTTP", null);
    ContentHeader playwrightHeader =
        new ContentHeader(
            "playwright", "feed", "https://example.test/playwright", "PLAYWRIGHT", null);

    when(repository.findWithoutFullTextForUrlExtraction(10))
        .thenReturn(
            List.of(
                new PendingFullTextTarget(httpHeader, FullTextMethod.HTTP),
                new PendingFullTextTarget(playwrightHeader, FullTextMethod.PLAYWRIGHT)));
    when(http.get(URI.create(httpHeader.url())))
        .thenReturn(
            new HttpFetchService.FetchedResource(
                URI.create(httpHeader.url()), "<html><body>HTTP body</body></html>"));
    when(rules.findBestRule(httpHeader.url())).thenReturn(Optional.empty());
    when(writer.saveIfAbsent(httpHeader, "HTTP body")).thenReturn(true);

    assertThat(service.extractPending(10)).isEqualTo(1);
    verify(http).get(URI.create(httpHeader.url()));
    verify(http, never()).get(URI.create(playwrightHeader.url()));
    verify(writer).saveIfAbsent(httpHeader, "HTTP body");
    verify(writer, never()).saveIfAbsent(any(), org.mockito.ArgumentMatchers.isNull());
  }
}
