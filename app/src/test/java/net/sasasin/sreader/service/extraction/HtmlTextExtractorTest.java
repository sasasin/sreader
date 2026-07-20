package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.dankito.readability4j.Article;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.domain.ExtractionPlan;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
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

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                url,
                "<html><body><p>First</p><p>Second</p><aside>Skip</aside></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT);
    assertThat(extracted.text()).isEqualTo("First\n\nSecond");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.CONFIGURED_XPATH);
  }

  @Test
  void fallsBackToBodyTextWhenXpathMisses() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.of(new ExtractRule("id", url, "//article")));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                url,
                "<html><body><main>Fallback body</main></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT);
    assertThat(extracted.text()).isEqualTo("Fallback body");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.BODY_TEXT);
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.CONFIGURED_XPATH_NO_MATCH);
  }

  @Test
  void readabilityExtractsArticleText() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
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
                ExtractionPlan.ExtractorKind.READABILITY);
    assertThat(extracted.text()).contains("main article text");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.READABILITY);
  }

  @Test
  void xpathOverrideIgnoresDbRuleAndReturnsOnlyMatching() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/a";
    when(rules.findBestRule(url)).thenReturn(Optional.of(new ExtractRule("r", url, "//article")));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                url,
                "<html><body><h1>Skip</h1><p class=\"c\">Hit</p><p"
                    + " class=\"c\">Two</p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                Optional.of("//p[@class='c']"));
    assertThat(extracted.text()).isEqualTo("Hit\n\nTwo");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.XPATH_OVERRIDE);
    verify(rules, never()).findBestRule(url);
  }

  @Test
  void xpathOverrideNoMatchIsNoContent() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    TextExtractionOutcome.NoContent noContent =
        (TextExtractionOutcome.NoContent)
            extractor.extract(
                "u",
                "<html><body><div>no</div></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                Optional.of("//p"));
    assertThat(noContent.reason()).isEqualTo(NoContentReason.XPATH_NO_MATCH);
  }

  @Test
  void xpathOverrideMatchedBlankIsNoContent() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    TextExtractionOutcome.NoContent noContent =
        (TextExtractionOutcome.NoContent)
            extractor.extract(
                "u",
                "<html><body><p>   </p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                Optional.of("//p"));
    assertThat(noContent.reason()).isEqualTo(NoContentReason.XPATH_MATCHED_EMPTY);
  }

  @Test
  void xpathOverrideInvalidIsFailedInvalidInput() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            extractor.extract(
                "u",
                "<html><body><div>no</div></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY,
                Optional.of("///bad["));
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.EXTRACT_TEXT);
  }

  @Test
  void xpathOverrideWithReadabilityKindStillUsesXpath() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                "u",
                "<html><body><article>Main</article></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY,
                Optional.of("//article"));
    assertThat(extracted.text()).isEqualTo("Main");
  }

  @Test
  void nullXpathOverrideIsRejected() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);

    assertThatThrownBy(
            () ->
                extractor.extract(
                    "https://example.test/article",
                    "<html><body><p>Body only</p></body></html>",
                    ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                    null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("xpathOverride");
  }

  @Test
  void emptyXpathOverrideUsesNormalExtractorPath() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                url,
                "<html><body><p>Via empty override</p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                Optional.empty());
    assertThat(extracted.text()).isEqualTo("Via empty override");
    verify(rules).findBestRule(url);
  }

  @Test
  void blankXpathOverrideIsFailedInvalidInput() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";

    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            extractor.extract(
                url,
                "<html><body><p>ignored</p></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT,
                Optional.of(""));
    assertThat(failed.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);
    verify(rules, never()).findBestRule(url);
  }

  @Test
  void threeArgExtractDelegatesWithEmptyOverride() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                url,
                "<html><body>Three-arg body</body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT);
    assertThat(extracted.text()).isEqualTo("Three-arg body");
  }

  @Test
  void noRuleFallsBackToBodyText() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractByXpathOrBody(
                url, "<html><body><div>No rule body</div></body></html>");
    assertThat(extracted.text()).isEqualTo("No rule body");
  }

  @Test
  void ruleWithBlankXpathTextFallsBackToBody() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.of(new ExtractRule("id", url, "//p")));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                url,
                "<html><body><p>   </p><div>Real body</div></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT);
    assertThat(extracted.text()).isEqualTo("Real body");
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.CONFIGURED_XPATH_EMPTY);
  }

  @Test
  void ruleWithInvalidXpathFallsBackToBodyWithoutThrowing() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url))
        .thenReturn(Optional.of(new ExtractRule("id", url, "///invalid[")));

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                url,
                "<html><body><main>Safe body</main></body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT);
    assertThat(extracted.text()).isEqualTo("Safe body");
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.CONFIGURED_XPATH_INVALID);
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

    assertThat(extractor.extractByXpath(document, "//div"))
        .isInstanceOf(HtmlTextExtractor.XpathExtractionAttempt.NoMatch.class);
    assertThat(extractor.extractByXpath(document, "//p[1]"))
        .isEqualTo(new HtmlTextExtractor.XpathExtractionAttempt.Matched("One"));
    assertThat(extractor.extractByXpath(document, "//p"))
        .isEqualTo(new HtmlTextExtractor.XpathExtractionAttempt.Matched("One\n\nTwo"));
    assertThat(extractor.extractByXpath(document, "//span"))
        .isEqualTo(new HtmlTextExtractor.XpathExtractionAttempt.Matched(""));
    assertThat(extractor.extractByXpath(document, "///bad["))
        .isInstanceOf(HtmlTextExtractor.XpathExtractionAttempt.Invalid.class);
  }

  @Test
  void readabilityBlankOrNullFallsBackToBodyText() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    Article blankArticle = mock(Article.class);
    when(blankArticle.getTextContent()).thenReturn("   ");
    HtmlTextExtractor blankExtractor = new HtmlTextExtractor(rules, (url, html) -> blankArticle);

    TextExtractionOutcome.Extracted blank =
        (TextExtractionOutcome.Extracted)
            blankExtractor.extract(
                "https://example.test/a",
                "<html><body><p>Blank readability body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY);
    assertThat(blank.text()).isEqualTo("Blank readability body");
    assertThat(blank.decision().fallbackReason())
        .contains(ExtractionFallbackReason.READABILITY_EMPTY);

    Article nullArticle = mock(Article.class);
    when(nullArticle.getTextContent()).thenReturn(null);
    HtmlTextExtractor nullExtractor = new HtmlTextExtractor(rules, (url, html) -> nullArticle);

    TextExtractionOutcome.Extracted nullText =
        (TextExtractionOutcome.Extracted)
            nullExtractor.extract(
                "https://example.test/a",
                "<html><body><p>Null readability body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY);
    assertThat(nullText.text()).isEqualTo("Null readability body");
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

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                "https://example.test/a",
                "<html><body><p>Exception fallback body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY);
    assertThat(extracted.text()).isEqualTo("Exception fallback body");
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.READABILITY_FAILED);
  }

  @Test
  void readabilityNonblankFromInjectedParserIsReturned() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    Article article = mock(Article.class);
    when(article.getTextContent()).thenReturn("Injected article text");
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules, (url, html) -> article);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extract(
                "https://example.test/a",
                "<html><body><p>ignored body</p></body></html>",
                ExtractionPlan.ExtractorKind.READABILITY);
    assertThat(extracted.text()).isEqualTo("Injected article text");
  }

  @Test
  void bodyBlankIsNoContent() {
    ExtractRuleService rules = mock(ExtractRuleService.class);
    HtmlTextExtractor extractor = new HtmlTextExtractor(rules);
    String url = "https://example.test/article";
    when(rules.findBestRule(url)).thenReturn(Optional.empty());

    TextExtractionOutcome.NoContent noContent =
        (TextExtractionOutcome.NoContent)
            extractor.extract(
                url,
                "<html><body>   </body></html>",
                ExtractionPlan.ExtractorKind.XPATH_OR_BODY_TEXT);
    assertThat(noContent.reason()).isEqualTo(NoContentReason.BODY_TEXT_EMPTY);
  }
}
