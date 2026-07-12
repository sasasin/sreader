package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.dankito.readability4j.Article;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.domain.ExtractionPlan;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
    verify(rules, never()).findBestRule(url);
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

  @Test
  void nullXpathOverrideUsesNormalExtractorPath() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    assertThat(
            extractor.extract(
                url,
                "<html><body><p>Body only</p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                null))
        .isEqualTo("Body only");
    verify(rules).findBestRule(url);
  }

  @Test
  void emptyXpathOverrideUsesNormalExtractorPath() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    assertThat(
            extractor.extract(
                url,
                "<html><body><p>Via empty override</p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                Optional.empty()))
        .isEqualTo("Via empty override");
    verify(rules).findBestRule(url);
  }

  @Test
  void blankXpathOverrideReturnsEmptyWithoutDbLookup() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";

    assertThat(
            extractor.extract(
                url,
                "<html><body><p>ignored</p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                Optional.of("")))
        .isEmpty();
    assertThat(
            extractor.extract(
                url,
                "<html><body><p>ignored</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY,
                Optional.of("  \t  ")))
        .isEmpty();
    verify(rules, never()).findBestRule(url);
  }

  @Test
  @SuppressWarnings("unchecked")
  void presentXpathOverrideWithNullValueReturnsEmptyWithoutDbLookup() {
    // Optional cannot legally contain null; mock present+null to cover the defensive branch.
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    Optional<String> xpathOverride = mock(Optional.class);
    when(xpathOverride.isPresent()).thenReturn(true);
    when(xpathOverride.get()).thenReturn(null);

    assertThat(
            extractor.extract(
                "https://example.test/article",
                "<html><body><p>ignored</p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                xpathOverride))
        .isEmpty();
    verify(rules, never()).findBestRule("https://example.test/article");
  }

  @Test
  void threeArgExtractDelegatesWithEmptyOverride() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    assertThat(
            extractor.extract(
                url,
                "<html><body>Three-arg body</body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT))
        .isEqualTo("Three-arg body");
  }

  @Test
  void noRuleFallsBackToBodyText() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    assertThat(
            extractor.extractByXpathOrBody(
                url, "<html><body><div>No rule body</div></body></html>"))
        .isEqualTo("No rule body");
  }

  @Test
  void ruleWithBlankXpathTextFallsBackToBody() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.of(new ExtractRule("id", url, "//p")));

    assertThat(
            extractor.extract(
                url,
                "<html><body><p>   </p><div>Real body</div></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT))
        .isEqualTo("Real body");
  }

  @Test
  void ruleWithInvalidXpathFallsBackToBodyWithoutThrowing() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url))
        .thenReturn(Optional.of(new ExtractRule("id", url, "///invalid[")));

    assertThat(
            extractor.extract(
                url,
                "<html><body><main>Safe body</main></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT))
        .isEqualTo("Safe body");
  }

  @Test
  void extractByXpathCoversEmptySingleMultipleBlankAndInvalid() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    Document document =
        Jsoup.parse(
            """
            <html><body>
              <p>One</p>
              <p></p>
              <p>  </p>
              <p>Two</p>
              <span></span>
              <span>   </span>
            </body></html>
            """);

    assertThat(extractor.extractByXpath(document, "//div")).isEmpty();
    assertThat(extractor.extractByXpath(document, "//p[1]")).contains("One");
    assertThat(extractor.extractByXpath(document, "//p")).contains("One\n\nTwo");
    // blank elements are filtered; all-blank match still yields Optional.of("")
    assertThat(extractor.extractByXpath(document, "//span")).contains("");
    assertThat(extractor.extractByXpath(document, "///bad[")).isEmpty();
  }

  @Test
  void readabilityBlankOrNullFallsBackToBodyText() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    Article blankArticle = mock(Article.class);
    when(blankArticle.getTextContent()).thenReturn("   ");
    HtmlTextExtractor blankExtractor = new HtmlTextExtractor(rules, (url, html) -> blankArticle);

    assertThat(
            blankExtractor.extract(
                "https://example.test/a",
                "<html><body><p>Blank readability body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY))
        .isEqualTo("Blank readability body");

    Article nullArticle = mock(Article.class);
    when(nullArticle.getTextContent()).thenReturn(null);
    HtmlTextExtractor nullExtractor = new HtmlTextExtractor(rules, (url, html) -> nullArticle);

    assertThat(
            nullExtractor.extract(
                "https://example.test/a",
                "<html><body><p>Null readability body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY))
        .isEqualTo("Null readability body");
  }

  @Test
  void readabilityExceptionFallsBackToBodyText() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor =
        new HtmlTextExtractor(
            rules,
            (url, html) -> {
              throw new RuntimeException("readability failed");
            });

    assertThat(
            extractor.extract(
                "https://example.test/a",
                "<html><body><p>Exception fallback body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY))
        .isEqualTo("Exception fallback body");
  }

  @Test
  void readabilityNonblankFromInjectedParserIsReturned() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    Article article = mock(Article.class);
    when(article.getTextContent()).thenReturn("Injected article text");
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules, (url, html) -> article);

    assertThat(
            extractor.extract(
                "https://example.test/a",
                "<html><body><p>ignored body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY))
        .isEqualTo("Injected article text");
  }
}
