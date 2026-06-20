package net.sasasin.sreader.service;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FullTextExtractionService {

  private static final Logger logger = LoggerFactory.getLogger(FullTextExtractionService.class);

  private final ContentHeaderRepository contentHeaderRepository;
  private final ContentFullTextRepository contentFullTextRepository;
  private final ExtractRuleService extractRuleService;
  private final HttpFetchService httpFetchService;

  public FullTextExtractionService(
      ContentHeaderRepository contentHeaderRepository,
      ContentFullTextRepository contentFullTextRepository,
      ExtractRuleService extractRuleService,
      HttpFetchService httpFetchService) {
    this.contentHeaderRepository = contentHeaderRepository;
    this.contentFullTextRepository = contentFullTextRepository;
    this.extractRuleService = extractRuleService;
    this.httpFetchService = httpFetchService;
  }

  public int extractPending(int limit) {
    List<ContentHeader> headers = contentHeaderRepository.findWithoutFullText(limit);
    int inserted = 0;
    for (ContentHeader header : headers) {
      try {
        String fullText = extract(header);
        ContentFullText content =
            new ContentFullText(HashIds.md5(header.url()), header.id(), fullText);
        if (contentFullTextRepository.insertIfAbsent(content)) {
          inserted++;
        }
      } catch (Exception e) {
        logger.warn("Failed to extract full text from {}", header.url(), e);
      }
    }
    return inserted;
  }

  public String extract(ContentHeader header) throws IOException, InterruptedException {
    HttpFetchService.FetchedResource resource = httpFetchService.get(URI.create(header.url()));
    Document document = Jsoup.parse(resource.body(), resource.uri().toString());
    return extractRuleService
        .findBestRule(header.url())
        .map(rule -> extractByXpath(document, rule.extractRule()))
        .filter(text -> !text.isBlank())
        .orElseGet(() -> document.body() == null ? document.text() : document.body().text());
  }

  private String extractByXpath(Document document, String xpath) {
    try {
      Elements elements = document.selectXpath(xpath);
      if (elements.isEmpty()) {
        return "";
      }
      StringBuilder result = new StringBuilder();
      elements.forEach(element -> result.append(element.text()).append('\n'));
      return result.toString().trim();
    } catch (RuntimeException e) {
      return "";
    }
  }
}
