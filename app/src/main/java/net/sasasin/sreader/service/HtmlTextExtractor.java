package net.sasasin.sreader.service;

import java.util.Optional;
import java.util.stream.Collectors;
import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import net.dankito.readability4j.extended.Readability4JExtended;
import net.sasasin.sreader.domain.ExtractionPlan;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HtmlTextExtractor {

  @FunctionalInterface
  interface ReadabilityParser {
    Article parse(String url, String html);
  }

  private final ExtractRuleService extractRuleService;
  private final ReadabilityParser readabilityParser;

  @Autowired
  public HtmlTextExtractor(ExtractRuleService extractRuleService) {
    this(
        extractRuleService,
        (url, html) -> {
          Readability4J readability = new Readability4JExtended(url, html);
          return readability.parse();
        });
  }

  HtmlTextExtractor(ExtractRuleService extractRuleService, ReadabilityParser readabilityParser) {
    this.extractRuleService = extractRuleService;
    this.readabilityParser = readabilityParser;
  }

  public String extract(String url, String html, ExtractionPlan.ExtractorKind extractorKind) {
    return extract(url, html, extractorKind, Optional.empty());
  }

  public String extract(
      String url,
      String html,
      ExtractionPlan.ExtractorKind extractorKind,
      Optional<String> xpathOverride) {
    if (xpathOverride != null && xpathOverride.isPresent()) {
      String xp = xpathOverride.get();
      if (xp == null || xp.isBlank()) {
        return "";
      }
      Document document = Jsoup.parse(html, url);
      return extractByXpath(document, xp).orElse("");
    }
    return switch (extractorKind) {
      case XPATH_OR_BODY_TEXT -> extractByXpathOrBody(url, html);
      case READABILITY -> extractByReadability(url, html);
    };
  }

  String extractByXpathOrBody(String url, String html) {
    Document document = Jsoup.parse(html, url);
    return extractRuleService
        .findBestRule(url)
        .flatMap(rule -> extractByXpath(document, rule.extractRule()))
        .filter(text -> !text.isBlank())
        .orElseGet(() -> bodyText(document));
  }

  Optional<String> extractByXpath(Document document, String xpath) {
    try {
      Elements elements = document.selectXpath(xpath);
      if (elements.isEmpty()) {
        return Optional.empty();
      }
      String text =
          elements.eachText().stream()
              .filter(value -> !value.isBlank())
              .collect(Collectors.joining("\n\n"));
      return Optional.of(text);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  String extractByReadability(String url, String html) {
    try {
      Article article = readabilityParser.parse(url, html);
      String text = article.getTextContent();
      if (text != null && !text.isBlank()) {
        return text;
      }
    } catch (RuntimeException e) {
      // Fallback below keeps extraction resilient for pages Readability cannot parse.
    }
    return bodyText(Jsoup.parse(html, url));
  }

  private String bodyText(Document document) {
    return document.body() == null ? document.text() : document.body().text();
  }
}
