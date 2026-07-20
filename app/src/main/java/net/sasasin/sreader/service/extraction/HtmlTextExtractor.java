package net.sasasin.sreader.service.extraction;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import net.dankito.readability4j.extended.Readability4JExtended;
import net.sasasin.sreader.domain.ExtractionPlan;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
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

  sealed interface XpathExtractionAttempt
      permits XpathExtractionAttempt.Matched,
          XpathExtractionAttempt.NoMatch,
          XpathExtractionAttempt.Invalid {
    record Matched(String text) implements XpathExtractionAttempt {
      public Matched {
        Objects.requireNonNull(text, "text must not be null");
      }
    }

    record NoMatch() implements XpathExtractionAttempt {}

    record Invalid(RuntimeException cause) implements XpathExtractionAttempt {
      public Invalid {
        Objects.requireNonNull(cause, "cause must not be null");
      }
    }
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

  public TextExtractionOutcome extract(
      String url, String html, ExtractionPlan.ExtractorKind extractorKind) {
    return extract(url, html, extractorKind, Optional.empty());
  }

  public TextExtractionOutcome extract(
      String url,
      String html,
      ExtractionPlan.ExtractorKind extractorKind,
      Optional<String> xpathOverride) {
    Objects.requireNonNull(xpathOverride, "xpathOverride must not be null");
    if (xpathOverride.isPresent()) {
      return extractWithExplicitXpath(url, html, xpathOverride.get());
    }
    return switch (extractorKind) {
      case XPATH_OR_BODY_TEXT -> extractByXpathOrBody(url, html);
      case READABILITY -> extractByReadability(url, html);
    };
  }

  private TextExtractionOutcome extractWithExplicitXpath(String url, String html, String xpath) {
    if (xpath == null || xpath.isBlank()) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.EXTRACT_TEXT,
              FailureKind.INVALID_INPUT,
              url,
              "Explicit XPath override is blank"));
    }
    Document document = Jsoup.parse(html, url);
    return switch (extractByXpath(document, xpath)) {
      case XpathExtractionAttempt.Matched matched when !matched.text().isBlank() ->
          new TextExtractionOutcome.Extracted(
              matched.text(), ExtractionDecision.of(ExtractionSource.XPATH_OVERRIDE));
      case XpathExtractionAttempt.Matched ignored ->
          new TextExtractionOutcome.NoContent(
              NoContentReason.XPATH_MATCHED_EMPTY,
              ExtractionDecision.of(ExtractionSource.XPATH_OVERRIDE));
      case XpathExtractionAttempt.NoMatch ignored ->
          new TextExtractionOutcome.NoContent(
              NoContentReason.XPATH_NO_MATCH,
              ExtractionDecision.of(ExtractionSource.XPATH_OVERRIDE));
      case XpathExtractionAttempt.Invalid invalid ->
          new TextExtractionOutcome.Failed(
              OperationFailure.of(
                  FailureStage.EXTRACT_TEXT,
                  FailureKind.INVALID_INPUT,
                  url,
                  "Invalid explicit XPath: " + xpath,
                  invalid.cause()));
    };
  }

  TextExtractionOutcome extractByXpathOrBody(String url, String html) {
    Document document = Jsoup.parse(html, url);
    return extractRuleService
        .findBestRule(url)
        .map(rule -> applyConfiguredXpath(document, rule.extractRule()))
        .orElseGet(() -> bodyOutcome(document, Optional.empty()));
  }

  private TextExtractionOutcome applyConfiguredXpath(Document document, String xpath) {
    return switch (extractByXpath(document, xpath)) {
      case XpathExtractionAttempt.Matched matched when !matched.text().isBlank() ->
          new TextExtractionOutcome.Extracted(
              matched.text(), ExtractionDecision.of(ExtractionSource.CONFIGURED_XPATH));
      case XpathExtractionAttempt.Matched ignored ->
          bodyOutcome(document, Optional.of(ExtractionFallbackReason.CONFIGURED_XPATH_EMPTY));
      case XpathExtractionAttempt.NoMatch ignored ->
          bodyOutcome(document, Optional.of(ExtractionFallbackReason.CONFIGURED_XPATH_NO_MATCH));
      case XpathExtractionAttempt.Invalid ignored ->
          bodyOutcome(document, Optional.of(ExtractionFallbackReason.CONFIGURED_XPATH_INVALID));
    };
  }

  XpathExtractionAttempt extractByXpath(Document document, String xpath) {
    try {
      Elements elements = document.selectXpath(xpath);
      if (elements.isEmpty()) {
        return new XpathExtractionAttempt.NoMatch();
      }
      String text =
          elements.eachText().stream()
              .filter(value -> !value.isBlank())
              .collect(Collectors.joining("\n\n"));
      return new XpathExtractionAttempt.Matched(text);
    } catch (RuntimeException e) {
      return new XpathExtractionAttempt.Invalid(e);
    }
  }

  TextExtractionOutcome extractByReadability(String url, String html) {
    Document document = Jsoup.parse(html, url);
    try {
      Article article = readabilityParser.parse(url, html);
      String text = article.getTextContent();
      if (text != null && !text.isBlank()) {
        return new TextExtractionOutcome.Extracted(
            text, ExtractionDecision.of(ExtractionSource.READABILITY));
      }
      return bodyOutcome(document, Optional.of(ExtractionFallbackReason.READABILITY_EMPTY));
    } catch (RuntimeException e) {
      return bodyOutcome(document, Optional.of(ExtractionFallbackReason.READABILITY_FAILED));
    }
  }

  private TextExtractionOutcome bodyOutcome(
      Document document, Optional<ExtractionFallbackReason> fallbackReason) {
    String text = bodyText(document);
    ExtractionDecision decision =
        new ExtractionDecision(ExtractionSource.BODY_TEXT, fallbackReason);
    if (text == null || text.isBlank()) {
      return new TextExtractionOutcome.NoContent(NoContentReason.BODY_TEXT_EMPTY, decision);
    }
    return new TextExtractionOutcome.Extracted(text, decision);
  }

  private String bodyText(Document document) {
    return document.body() == null ? document.text() : document.body().text();
  }
}
