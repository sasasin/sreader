package net.sasasin.sreader.service.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.dankito.readability4j.Article;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.domain.FullTextMethod.HtmlExtractor;
import net.sasasin.sreader.service.autopagerize.PageSlice;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

/**
 * Extracts article text from a successful AutoPagerize pagination result. Network adapters are not
 * required; callers pass an already-built {@link PaginationResult.Succeeded}.
 */
@Service
public class PaginatedHtmlTextExtractor {

  private final HtmlTextExtractor htmlTextExtractor;
  private final ExtractRuleService extractRuleService;
  private final ReadabilityParser readabilityParser;

  public PaginatedHtmlTextExtractor(
      HtmlTextExtractor htmlTextExtractor,
      ExtractRuleService extractRuleService,
      ReadabilityParser readabilityParser) {
    this.htmlTextExtractor =
        Objects.requireNonNull(htmlTextExtractor, "htmlTextExtractor must not be null");
    this.extractRuleService =
        Objects.requireNonNull(extractRuleService, "extractRuleService must not be null");
    this.readabilityParser =
        Objects.requireNonNull(readabilityParser, "readabilityParser must not be null");
  }

  public PaginatedExtractionResult extract(
      PaginationResult.Succeeded pagination,
      HtmlExtractor extractor,
      Optional<String> xpathOverride) {
    Objects.requireNonNull(pagination, "pagination must not be null");
    Objects.requireNonNull(extractor, "extractor must not be null");
    Objects.requireNonNull(xpathOverride, "xpathOverride must not be null");

    if (pagination.matchedRule().isEmpty()) {
      TextExtractionOutcome single =
          htmlTextExtractor.extract(
              pagination.firstPage().finalUri().toString(),
              pagination.firstPage().html(),
              extractor,
              xpathOverride);
      return new PaginatedExtractionResult(single, contributionsFromSingle(single));
    }

    if (xpathOverride.isPresent()) {
      // Explicit override is probe-oriented: no pageElement fallback on miss/invalid.
      return extractWithExplicitOverride(pagination, xpathOverride);
    }

    return switch (extractor) {
      case XPATH_OR_BODY_TEXT -> extractMatchedXpathPath(pagination);
      case READABILITY -> extractMatchedReadabilityPath(pagination);
    };
  }

  public TextExtractionOutcome extractOutcome(
      PaginationResult.Succeeded pagination,
      HtmlExtractor extractor,
      Optional<String> xpathOverride) {
    return extract(pagination, extractor, xpathOverride).outcome();
  }

  private PaginatedExtractionResult extractMatchedXpathPath(PaginationResult.Succeeded pagination) {
    List<PageTextContribution> contributions = new ArrayList<>();
    List<String> texts = new ArrayList<>();

    for (PageSlice page : pagination.pages()) {
      String url = page.finalUri().toString();
      Optional<ExtractRule> rule = extractRuleService.findBestRule(url);
      if (rule.isPresent()) {
        Document document = Jsoup.parse(page.html(), url);
        HtmlTextExtractor.XpathExtractionAttempt attempt =
            htmlTextExtractor.extractByXpath(document, rule.get().extractRule());
        if (attempt instanceof HtmlTextExtractor.XpathExtractionAttempt.Matched matched
            && !matched.text().isBlank()) {
          contributions.add(
              new PageTextContribution(
                  page.pageNumber(),
                  ExtractionSource.CONFIGURED_XPATH,
                  Optional.empty(),
                  matched.text()));
          texts.add(matched.text());
          continue;
        }
        Optional<ExtractionFallbackReason> reason = fallbackReasonFor(attempt);
        PaginatedExtractionResult failed =
            addPageElementOrEmpty(page, contributions, texts, reason);
        if (failed != null) {
          return failed;
        }
        continue;
      }

      PaginatedExtractionResult failed =
          addPageElementOrEmpty(page, contributions, texts, Optional.empty());
      if (failed != null) {
        return failed;
      }
    }

    return join(contributions, texts);
  }

  private PaginatedExtractionResult extractMatchedReadabilityPath(
      PaginationResult.Succeeded pagination) {
    List<PageTextContribution> contributions = new ArrayList<>();
    List<String> texts = new ArrayList<>();
    for (PageSlice page : pagination.pages()) {
      String url = page.finalUri().toString();
      Optional<ExtractionFallbackReason> fallback = Optional.empty();
      try {
        Article article = readabilityParser.parse(url, page.html());
        String text = article.getTextContent();
        if (text != null && !text.isBlank()) {
          contributions.add(
              new PageTextContribution(
                  page.pageNumber(), ExtractionSource.READABILITY, Optional.empty(), text));
          texts.add(text);
          continue;
        }
        fallback = Optional.of(ExtractionFallbackReason.READABILITY_EMPTY);
      } catch (RuntimeException e) {
        fallback = Optional.of(ExtractionFallbackReason.READABILITY_FAILED);
      }
      PaginatedExtractionResult failed =
          addPageElementOrEmpty(page, contributions, texts, fallback);
      if (failed != null) {
        return failed;
      }
    }
    return join(contributions, texts);
  }

  private PaginatedExtractionResult extractWithExplicitOverride(
      PaginationResult.Succeeded pagination, Optional<String> xpathOverride) {
    List<PageTextContribution> contributions = new ArrayList<>();
    List<String> texts = new ArrayList<>();
    for (PageSlice page : pagination.pages()) {
      TextExtractionOutcome overrideOutcome =
          htmlTextExtractor.extract(
              page.finalUri().toString(),
              page.html(),
              HtmlExtractor.XPATH_OR_BODY_TEXT,
              xpathOverride);
      if (overrideOutcome instanceof TextExtractionOutcome.Extracted extracted) {
        contributions.add(
            new PageTextContribution(
                page.pageNumber(),
                extracted.decision().source(),
                extracted.decision().fallbackReason(),
                extracted.text()));
        texts.add(extracted.text());
        continue;
      }
      return new PaginatedExtractionResult(overrideOutcome, List.copyOf(contributions));
    }
    return join(contributions, texts);
  }

  private static Optional<ExtractionFallbackReason> fallbackReasonFor(
      HtmlTextExtractor.XpathExtractionAttempt attempt) {
    return switch (attempt) {
      case HtmlTextExtractor.XpathExtractionAttempt.Matched ignored ->
          Optional.of(ExtractionFallbackReason.CONFIGURED_XPATH_EMPTY);
      case HtmlTextExtractor.XpathExtractionAttempt.NoMatch ignored ->
          Optional.of(ExtractionFallbackReason.CONFIGURED_XPATH_NO_MATCH);
      case HtmlTextExtractor.XpathExtractionAttempt.Invalid ignored ->
          Optional.of(ExtractionFallbackReason.CONFIGURED_XPATH_INVALID);
    };
  }

  /**
   * Adds pageElement text when non-blank; otherwise returns a no-content result. Returns null when
   * the page was appended successfully.
   */
  private static PaginatedExtractionResult addPageElementOrEmpty(
      PageSlice page,
      List<PageTextContribution> contributions,
      List<String> texts,
      Optional<ExtractionFallbackReason> fallbackReason) {
    String pageElementText = page.pageElementText();
    if (pageElementText.isBlank()) {
      contributions.add(
          new PageTextContribution(
              page.pageNumber(), ExtractionSource.PAGE_ELEMENT, fallbackReason, ""));
      return new PaginatedExtractionResult(
          new TextExtractionOutcome.NoContent(
              NoContentReason.PAGE_ELEMENT_EMPTY,
              new ExtractionDecision(ExtractionSource.PAGE_ELEMENT, fallbackReason)),
          List.copyOf(contributions));
    }
    contributions.add(
        new PageTextContribution(
            page.pageNumber(), ExtractionSource.PAGE_ELEMENT, fallbackReason, pageElementText));
    texts.add(pageElementText);
    return null;
  }

  private static List<PageTextContribution> contributionsFromSingle(TextExtractionOutcome single) {
    if (single instanceof TextExtractionOutcome.Extracted extracted) {
      return List.of(
          new PageTextContribution(
              1,
              extracted.decision().source(),
              extracted.decision().fallbackReason(),
              extracted.text()));
    }
    if (single instanceof TextExtractionOutcome.NoContent noContent) {
      return List.of(
          new PageTextContribution(
              1, noContent.decision().source(), noContent.decision().fallbackReason(), ""));
    }
    return List.of();
  }

  private static PaginatedExtractionResult join(
      List<PageTextContribution> contributions, List<String> texts) {
    String joined = texts.stream().collect(Collectors.joining("\n\n"));
    ExtractionDecision decision = summaryDecision(contributions);
    if (joined.isBlank()) {
      return new PaginatedExtractionResult(
          new TextExtractionOutcome.NoContent(NoContentReason.PAGE_ELEMENT_EMPTY, decision),
          List.copyOf(contributions));
    }
    return new PaginatedExtractionResult(
        new TextExtractionOutcome.Extracted(joined, decision), List.copyOf(contributions));
  }

  private static ExtractionDecision summaryDecision(List<PageTextContribution> contributions) {
    if (contributions.isEmpty()) {
      return ExtractionDecision.of(ExtractionSource.PAGE_ELEMENT);
    }
    long distinctSources =
        contributions.stream().map(PageTextContribution::source).distinct().count();
    if (distinctSources > 1) {
      return ExtractionDecision.of(ExtractionSource.MIXED);
    }
    PageTextContribution first = contributions.getFirst();
    boolean sameFallback =
        contributions.stream().allMatch(c -> c.fallbackReason().equals(first.fallbackReason()));
    if (sameFallback && first.fallbackReason().isPresent()) {
      return new ExtractionDecision(first.source(), first.fallbackReason());
    }
    return ExtractionDecision.of(first.source());
  }
}
