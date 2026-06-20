package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.junit.jupiter.api.Test;

class FullTextExtractionServiceTest {

  @Test
  void extractsTextWithXpathRule() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    FullTextExtractionService service =
        new FullTextExtractionService(
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextRepository.class),
            rules,
            http);
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
            mock(ContentHeaderRepository.class),
            mock(ContentFullTextRepository.class),
            rules,
            http);
    ContentHeader header =
        new ContentHeader("id", "feed", "https://example.test/no-rule", "title", null);
    when(http.get(URI.create(header.url())))
        .thenReturn(
            new HttpFetchService.FetchedResource(
                URI.create(header.url()), "<html><body><main>Fallback body</main></body></html>"));
    when(rules.findBestRule(header.url())).thenReturn(Optional.empty());

    assertThat(service.extract(header)).isEqualTo("Fallback body");
  }
}
