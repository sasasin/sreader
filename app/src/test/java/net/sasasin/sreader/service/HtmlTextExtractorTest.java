package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.domain.ExtractionPlan;
import org.junit.jupiter.api.Test;

class HtmlTextExtractorTest {

  @Test
  void extractsByXpathAndJoinsMultipleElementsWithBlankLine() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.of(new ExtractRule("id", url, "//p")));

    assertThat(
            extractor.extract(
                url,
                "<html><body><p>First</p><p>Second</p><aside>Skip</aside></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT))
        .isEqualTo("First\n\nSecond");
  }

  @Test
  void fallsBackToBodyTextWhenXpathMisses() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.of(new ExtractRule("id", url, "//article")));

    assertThat(
            extractor.extract(
                url,
                "<html><body><main>Fallback body</main></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT))
        .isEqualTo("Fallback body");
  }

  @Test
  void readabilityExtractsArticleText() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    assertThat(
            extractor.extract(
                "https://example.test/article",
                """
                <html><head><title>Title</title></head><body>
                <article>
                  <h1>Title</h1>
                  <p>This is the main article text with enough words for readability.</p>
                </article>
                </body></html>
                """,
                ExtractionPlan.ExtractorKind.READABILITY))
        .contains("main article text");
  }

  @Test
  void xpathOverrideIgnoresDbRuleAndReturnsOnlyMatching() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/a";
    // even if rule exists, override should win
    when(rules.findBestRule(url)).thenReturn(Optional.of(new ExtractRule("r", url, "//article")));

    String out =
        extractor.extract(
            url,
            "<html><body><h1>Skip</h1><p class=\"c\">Hit</p><p class=\"c\">Two</p></body></html>",
            ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
            Optional.of("//p[@class='c']"));
    assertThat(out).isEqualTo("Hit\n\nTwo");
  }

  @Test
  void xpathOverrideReturnsEmptyOnNoMatchOrBadXpath() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    String out1 =
        extractor.extract(
            "u",
            "<html><body><div>no</div></body></html>",
            ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
            Optional.of("//p"));
    assertThat(out1).isEqualTo("");

    String out2 =
        extractor.extract(
            "u",
            "<html><body><div>no</div></body></html>",
            ExtractionPlan.ExtractorKind.READABILITY,
            Optional.of("///bad["));
    assertThat(out2).isEqualTo("");
  }

  @Test
  void xpathOverrideWithReadabilityKindStillUsesXpath() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    String out =
        extractor.extract(
            "u",
            "<html><body><article>Main</article></body></html>",
            ExtractionPlan.ExtractorKind.READABILITY,
            Optional.of("//article"));
    assertThat(out).isEqualTo("Main");
  }
}
