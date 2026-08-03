package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.domain.FullTextMethod.HtmlExtractor;
import net.sasasin.sreader.service.autopagerize.CompiledAutoPagerizeRule;
import net.sasasin.sreader.service.autopagerize.PageSlice;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaginatedHtmlTextExtractorTest {

  private ExtractRuleService rules;
  private PaginatedHtmlTextExtractor extractor;

  @BeforeEach
  void setUp() {
    rules = mock(ExtractRuleService.class);
    extractor =
        new PaginatedHtmlTextExtractor(
            new HtmlTextExtractor(rules, new ReadabilityArticleParser()),
            rules,
            new ReadabilityArticleParser());
  }

  @Test
  void noRuleUsesExistingSinglePageExtractor() {
    URI uri = URI.create("https://example.test/article");
    when(rules.findBestRule(uri.toString())).thenReturn(Optional.empty());
    PaginationResult.Succeeded pagination =
        noRuleSuccess(uri, "<html><body><main>Single page body</main></body></html>");
    PaginatedExtractionResult result =
        extractor.extract(pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(result.outcome()).isInstanceOf(TextExtractionOutcome.Extracted.class);
    TextExtractionOutcome.Extracted extracted = (TextExtractionOutcome.Extracted) result.outcome();
    assertThat(extracted.text()).isEqualTo("Single page body");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.BODY_TEXT);
  }

  @Test
  void configuredXpathSuccessPerPage() {
    URI p1 = URI.create("https://example.test/a/1");
    URI p2 = URI.create("https://example.test/a/2");
    when(rules.findBestRule(p1.toString()))
        .thenReturn(Optional.of(new ExtractRule("r", "https://example.test/", "//p[@class='x']")));
    when(rules.findBestRule(p2.toString()))
        .thenReturn(Optional.of(new ExtractRule("r", "https://example.test/", "//p[@class='x']")));
    PaginationResult.Succeeded pagination =
        matchedSuccess(
            List.of(
                slice(1, p1, htmlWithXpathAndPageElement("xpath-one", "pe-one"), "pe-one"),
                slice(2, p2, htmlWithXpathAndPageElement("xpath-two", "pe-two"), "pe-two")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(
                pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(extracted.text()).isEqualTo("xpath-one\n\nxpath-two");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.CONFIGURED_XPATH);
  }

  @Test
  void configuredXpathFallsBackToPageElement() {
    URI p1 = URI.create("https://example.test/a/1");
    when(rules.findBestRule(p1.toString()))
        .thenReturn(
            Optional.of(new ExtractRule("r", "https://example.test/", "//p[@class='miss']")));
    PaginationResult.Succeeded pagination =
        matchedSuccess(
            List.of(
                slice(
                    1, p1, htmlWithXpathAndPageElement("hidden", "page-element"), "page-element")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(
                pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(extracted.text()).isEqualTo("page-element");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.PAGE_ELEMENT);
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.CONFIGURED_XPATH_NO_MATCH);
  }

  @Test
  void perPageReadability() {
    URI p1 = URI.create("https://example.test/a/1");
    URI p2 = URI.create("https://example.test/a/2");
    PaginationResult.Succeeded pagination =
        matchedSuccess(
            List.of(
                slice(
                    1,
                    p1,
                    readabilityHtml("Title", "First page article text enough words."),
                    "pe1"),
                slice(
                    2,
                    p2,
                    readabilityHtml("Title", "Second page article text enough words."),
                    "pe2")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(pagination, HtmlExtractor.READABILITY, Optional.empty());
    assertThat(extracted.text()).contains("First page article text");
    assertThat(extracted.text()).contains("Second page article text");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.READABILITY);
  }

  @Test
  void readabilityFallsBackToPageElement() {
    URI p1 = URI.create("https://example.test/a/1");
    ReadabilityParser failing =
        (url, html) -> {
          throw new IllegalStateException("readability boom");
        };
    PaginatedHtmlTextExtractor withFailingReadability =
        new PaginatedHtmlTextExtractor(new HtmlTextExtractor(rules, failing), rules, failing);
    PaginationResult.Succeeded pagination =
        matchedSuccess(
            List.of(
                slice(1, p1, pageElementOnly("fallback-page-element"), "fallback-page-element")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            withFailingReadability.extractOutcome(
                pagination, HtmlExtractor.READABILITY, Optional.empty());
    assertThat(extracted.text()).isEqualTo("fallback-page-element");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.PAGE_ELEMENT);
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.READABILITY_FAILED);
  }

  @Test
  void joinsPagesInOrderWithBlankLines() {
    URI p1 = URI.create("https://example.test/a/1");
    URI p2 = URI.create("https://example.test/a/2");
    URI p3 = URI.create("https://example.test/a/3");
    when(rules.findBestRule(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    PaginationResult.Succeeded pagination =
        matchedSuccess(
            List.of(
                slice(1, p1, pageElementOnly("A"), "A"),
                slice(2, p2, pageElementOnly("B"), "B"),
                slice(3, p3, pageElementOnly("C"), "C")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(
                pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(extracted.text()).isEqualTo("A\n\nB\n\nC");
  }

  @Test
  void explicitXpathOverrideNoMatchIsHardNoContentNotPageElementFallback() {
    URI p1 = URI.create("https://example.test/a/1");
    PaginationResult.Succeeded pagination =
        matchedSuccess(List.of(slice(1, p1, pageElementOnly("should-not-use"), "should-not-use")));
    TextExtractionOutcome outcome =
        extractor.extractOutcome(
            pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.of("//p[@class='miss']"));
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.NoContent.class);
    TextExtractionOutcome.NoContent noContent = (TextExtractionOutcome.NoContent) outcome;
    assertThat(noContent.reason()).isEqualTo(NoContentReason.XPATH_NO_MATCH);
    assertThat(noContent.decision().source()).isEqualTo(ExtractionSource.XPATH_OVERRIDE);
  }

  @Test
  void emptyPageElementIsNoContent() {
    URI p1 = URI.create("https://example.test/a/1");
    when(rules.findBestRule(p1.toString())).thenReturn(Optional.empty());
    PaginationResult.Succeeded pagination =
        matchedSuccess(List.of(slice(1, p1, pageElementOnly(""), "")));
    TextExtractionOutcome outcome =
        extractor.extractOutcome(pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.NoContent.class);
    assertThat(((TextExtractionOutcome.NoContent) outcome).reason())
        .isEqualTo(NoContentReason.PAGE_ELEMENT_EMPTY);
  }

  @Test
  void configuredXpathEmptyFallsBackToPageElement() {
    URI p1 = URI.create("https://example.test/a/1");
    when(rules.findBestRule(p1.toString()))
        .thenReturn(Optional.of(new ExtractRule("r", "https://example.test/", "//p[@class='x']")));
    String html =
        "<html><body><p class=\"x\">   </p><div"
            + " class=\"body\">from-page-element</div></body></html>";
    PaginationResult.Succeeded pagination =
        matchedSuccess(List.of(slice(1, p1, html, "from-page-element")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(
                pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(extracted.text()).isEqualTo("from-page-element");
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.CONFIGURED_XPATH_EMPTY);
  }

  @Test
  void configuredXpathInvalidFallsBackToPageElement() {
    URI p1 = URI.create("https://example.test/a/1");
    when(rules.findBestRule(p1.toString()))
        .thenReturn(Optional.of(new ExtractRule("r", "https://example.test/", "//[")));
    PaginationResult.Succeeded pagination =
        matchedSuccess(List.of(slice(1, p1, pageElementOnly("pe"), "pe")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(
                pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(extracted.text()).isEqualTo("pe");
    assertThat(extracted.decision().fallbackReason())
        .contains(ExtractionFallbackReason.CONFIGURED_XPATH_INVALID);
  }

  @Test
  void explicitOverrideBlankIsFailed() {
    URI p1 = URI.create("https://example.test/a/1");
    PaginationResult.Succeeded pagination =
        matchedSuccess(List.of(slice(1, p1, pageElementOnly("pe"), "pe")));
    TextExtractionOutcome outcome =
        extractor.extractOutcome(pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.of("  "));
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.Failed.class);
  }

  @Test
  void configuredXpathEmptyAndBlankPageElementIsNoContent() {
    URI p1 = URI.create("https://example.test/a/1");
    when(rules.findBestRule(p1.toString()))
        .thenReturn(Optional.of(new ExtractRule("r", "https://example.test/", "//p[@class='x']")));
    String html = "<html><body><p class=\"x\">   </p><div class=\"body\"></div></body></html>";
    PaginationResult.Succeeded pagination = matchedSuccess(List.of(slice(1, p1, html, "")));
    TextExtractionOutcome outcome =
        extractor.extractOutcome(pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.NoContent.class);
    assertThat(((TextExtractionOutcome.NoContent) outcome).reason())
        .isEqualTo(NoContentReason.PAGE_ELEMENT_EMPTY);
  }

  @Test
  void readabilityEmptyThenBlankPageElementIsNoContent() {
    URI p1 = URI.create("https://example.test/a/1");
    net.dankito.readability4j.Article emptyArticle = mock(net.dankito.readability4j.Article.class);
    when(emptyArticle.getTextContent()).thenReturn("  ");
    ReadabilityParser emptyParser = (url, html) -> emptyArticle;
    PaginatedHtmlTextExtractor withEmpty =
        new PaginatedHtmlTextExtractor(
            new HtmlTextExtractor(rules, emptyParser), rules, emptyParser);
    PaginationResult.Succeeded pagination =
        matchedSuccess(List.of(slice(1, p1, pageElementOnly(""), "")));
    TextExtractionOutcome outcome =
        withEmpty.extractOutcome(pagination, HtmlExtractor.READABILITY, Optional.empty());
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.NoContent.class);
  }

  @Test
  void noRuleBodyEmptyIsNoContent() {
    URI uri = URI.create("https://example.test/article");
    when(rules.findBestRule(uri.toString())).thenReturn(Optional.empty());
    PaginationResult.Succeeded pagination = noRuleSuccess(uri, "<html><body></body></html>");
    PaginatedExtractionResult result =
        extractor.extract(pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(result.outcome()).isInstanceOf(TextExtractionOutcome.NoContent.class);
  }

  @Test
  void explicitOverrideOnReadabilityPathSucceedsAcrossPages() {
    URI p1 = URI.create("https://example.test/a/1");
    URI p2 = URI.create("https://example.test/a/2");
    PaginationResult.Succeeded pagination =
        matchedSuccess(
            List.of(
                slice(1, p1, htmlWithXpathAndPageElement("o1", "pe1"), "pe1"),
                slice(2, p2, htmlWithXpathAndPageElement("o2", "pe2"), "pe2")));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(
                pagination, HtmlExtractor.READABILITY, Optional.of("//p[@class='x']"));
    assertThat(extracted.text()).isEqualTo("o1\n\no2");
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.XPATH_OVERRIDE);
  }

  @Test
  void noRuleReadabilityUsesSinglePageExtractor() {
    URI uri = URI.create("https://example.test/article");
    PaginationResult.Succeeded pagination =
        noRuleSuccess(uri, readabilityHtml("Title", "Enough words for readability article text."));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            extractor.extractOutcome(pagination, HtmlExtractor.READABILITY, Optional.empty());
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.READABILITY);
  }

  @Test
  void mixedSourcesAreSummarizedAsMixed() {
    URI p1 = URI.create("https://example.test/a/1");
    URI p2 = URI.create("https://example.test/a/2");
    when(rules.findBestRule(p1.toString()))
        .thenReturn(Optional.of(new ExtractRule("r", "https://example.test/", "//p[@class='x']")));
    when(rules.findBestRule(p2.toString())).thenReturn(Optional.empty());
    PaginationResult.Succeeded pagination =
        matchedSuccess(
            List.of(
                slice(1, p1, htmlWithXpathAndPageElement("xpath", "pe1"), "pe1"),
                slice(2, p2, pageElementOnly("pe2"), "pe2")));
    PaginatedExtractionResult result =
        extractor.extract(pagination, HtmlExtractor.XPATH_OR_BODY_TEXT, Optional.empty());
    assertThat(result.mixedSources()).isTrue();
    TextExtractionOutcome.Extracted extracted = (TextExtractionOutcome.Extracted) result.outcome();
    assertThat(extracted.decision().source()).isEqualTo(ExtractionSource.MIXED);
    assertThat(extracted.text()).isEqualTo("xpath\n\npe2");
  }

  private static PaginationResult.Succeeded noRuleSuccess(URI uri, String html) {
    PageSnapshot first = PageSnapshot.ofUtf8(uri, uri, html);
    return new PaginationResult.Succeeded(
        first,
        Optional.empty(),
        List.of(PageSlice.withoutPageElement(1, first)),
        PaginationStopReason.NO_MATCHING_RULE);
  }

  private static PaginationResult.Succeeded matchedSuccess(List<PageSlice> pages) {
    PageSlice first = pages.getFirst();
    PageSnapshot snapshot =
        new PageSnapshot(first.requestedUri(), first.finalUri(), first.html(), first.byteSize());
    CompiledAutoPagerizeRule rule =
        new CompiledAutoPagerizeRule(
            1L,
            0,
            0,
            "r",
            Pattern.compile("^https://example\\.test/"),
            "^https://example\\.test/",
            "//a[@rel='next']",
            "//div[@class='body']",
            null,
            null);
    return new PaginationResult.Succeeded(
        snapshot, Optional.of(rule), pages, PaginationStopReason.NO_NEXT_LINK);
  }

  private static PageSlice slice(int number, URI uri, String html, String pageElementText) {
    return new PageSlice(
        number,
        uri,
        uri,
        html,
        "<div class=\"body\">" + pageElementText + "</div>",
        pageElementText,
        Optional.empty(),
        Optional.of("hash-" + number),
        PageSnapshot.utf8ByteSize(html));
  }

  private static String pageElementOnly(String text) {
    return "<html><body><div class=\"body\">" + text + "</div></body></html>";
  }

  private static String htmlWithXpathAndPageElement(String xpathText, String pageElementText) {
    return "<html><body><p class=\"x\">"
        + xpathText
        + "</p><div class=\"body\">"
        + pageElementText
        + "</div></body></html>";
  }

  private static String readabilityHtml(String title, String paragraph) {
    return """
    <html><head><title>%s</title></head><body>
    <article><h1>%s</h1><p>%s</p></article>
    <div class="body">page-element-backup</div>
    </body></html>
    """
        .formatted(title, title, paragraph);
  }
}
