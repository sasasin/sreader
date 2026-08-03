package net.sasasin.sreader.service.autopagerize;

import java.util.Objects;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Selects the first AutoPagerize rule whose URL pattern matches and whose nextLink and pageElement
 * XPath both return at least one element on the first page.
 */
@Component
public class AutoPagerizeRuleMatcher {

  private final AutoPagerizePageAnalyzer pageAnalyzer;

  public AutoPagerizeRuleMatcher(AutoPagerizePageAnalyzer pageAnalyzer) {
    this.pageAnalyzer = Objects.requireNonNull(pageAnalyzer, "pageAnalyzer must not be null");
  }

  public Optional<CompiledAutoPagerizeRule> findMatchingRule(
      PageSnapshot firstPage, AutoPagerizeRuleSnapshot snapshot) {
    Objects.requireNonNull(firstPage, "firstPage must not be null");
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    String url = firstPage.finalUri().toString();
    Document document = pageAnalyzer.parse(firstPage);
    for (CompiledAutoPagerizeRule rule : snapshot.rules()) {
      if (!rule.urlPattern().matcher(url).find()) {
        continue;
      }
      AutoPagerizePageAnalyzer.XPathPresence nextPresence =
          pageAnalyzer.presence(document, rule.nextLinkXpath());
      if (nextPresence != AutoPagerizePageAnalyzer.XPathPresence.PRESENT) {
        continue;
      }
      AutoPagerizePageAnalyzer.XPathPresence pagePresence =
          pageAnalyzer.presence(document, rule.pageElementXpath());
      if (pagePresence != AutoPagerizePageAnalyzer.XPathPresence.PRESENT) {
        continue;
      }
      return Optional.of(rule);
    }
    return Optional.empty();
  }
}
