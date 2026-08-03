package net.sasasin.sreader.service.autopagerize;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/** Applies AutoPagerize nextLink / pageElement XPath to a loaded page. */
@Component
public class AutoPagerizePageAnalyzer {

  public Document parse(PageSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    return Jsoup.parse(snapshot.html(), snapshot.finalUri().toString());
  }

  /**
   * Returns whether the XPath selects at least one element. Distinguishes empty selection from
   * invalid XPath evaluation.
   */
  public XPathPresence presence(Document document, String xpath) {
    Objects.requireNonNull(document, "document must not be null");
    Objects.requireNonNull(xpath, "xpath must not be null");
    try {
      Elements elements = document.selectXpath(xpath);
      if (elements.isEmpty()) {
        return XPathPresence.EMPTY;
      }
      return XPathPresence.PRESENT;
    } catch (RuntimeException e) {
      return XPathPresence.INVALID;
    }
  }

  public PageAnalysis analyze(PageSnapshot snapshot, CompiledAutoPagerizeRule rule) {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    Objects.requireNonNull(rule, "rule must not be null");
    Document document = parse(snapshot);
    Elements pageElements = document.selectXpath(rule.pageElementXpath());
    if (pageElements.isEmpty()) {
      throw new IllegalStateException("pageElement XPath matched zero elements");
    }
    String outerHtml = pageElements.stream().map(Element::outerHtml).collect(Collectors.joining());
    String text =
        pageElements.eachText().stream()
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining("\n\n"));
    String hash = PageElementContentHasher.sha256Hex(outerHtml);

    Elements nextLinks = document.selectXpath(rule.nextLinkXpath());
    if (nextLinks.isEmpty()) {
      return new PageAnalysis(
          outerHtml, text, hash, Optional.empty(), Optional.of(PageAnalysis.NextLinkIssue.MISSING));
    }
    Element first = nextLinks.first();
    Optional<String> raw = PaginationUriSupport.firstNonBlankAttribute(first);
    if (raw.isEmpty()) {
      return new PageAnalysis(
          outerHtml, text, hash, Optional.empty(), Optional.of(PageAnalysis.NextLinkIssue.MISSING));
    }
    Optional<URI> resolved =
        PaginationUriSupport.resolveNextCandidate(snapshot.finalUri(), document, raw.get());
    if (resolved.isEmpty()) {
      return new PageAnalysis(
          outerHtml,
          text,
          hash,
          Optional.empty(),
          Optional.of(PageAnalysis.NextLinkIssue.INVALID_URI));
    }
    URI next = resolved.get();
    if (!PaginationUriSupport.isAllowedScheme(next)) {
      return new PageAnalysis(
          outerHtml,
          text,
          hash,
          Optional.of(next),
          Optional.of(PageAnalysis.NextLinkIssue.UNSUPPORTED_SCHEME));
    }
    if (PaginationUriSupport.hasUserInfo(next)) {
      return new PageAnalysis(
          outerHtml,
          text,
          hash,
          Optional.of(next),
          Optional.of(PageAnalysis.NextLinkIssue.USERINFO_REJECTED));
    }
    return new PageAnalysis(
        outerHtml, text, hash, Optional.of(next), Optional.of(PageAnalysis.NextLinkIssue.NONE));
  }

  public enum XPathPresence {
    PRESENT,
    EMPTY,
    INVALID
  }
}
