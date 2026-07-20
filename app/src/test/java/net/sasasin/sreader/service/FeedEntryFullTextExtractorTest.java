package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.junit.jupiter.api.Test;

class FeedEntryFullTextExtractorTest {

  private final FeedEntryFullTextExtractor extractor = new FeedEntryFullTextExtractor();

  @Test
  void extractsPlainTextContent() {
    SyndEntryImpl entry = new SyndEntryImpl();
    SyndContentImpl content = new SyndContentImpl();
    content.setType("text/plain");
    content.setValue("Plain feed body");
    entry.getContents().add(content);

    TextExtractionOutcome outcome = extractor.extract(entry);
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.Extracted.class);
    assertThat(((TextExtractionOutcome.Extracted) outcome).text()).isEqualTo("Plain feed body");
    assertThat(((TextExtractionOutcome.Extracted) outcome).decision().source())
        .isEqualTo(ExtractionSource.FEED);
  }

  @Test
  void convertsHtmlContentToPlainText() {
    SyndEntryImpl entry = new SyndEntryImpl();
    SyndContentImpl content = new SyndContentImpl();
    content.setType("text/html");
    content.setValue("<article><p>Hello <strong>feed</strong></p></article>");
    entry.getContents().add(content);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted) extractor.extract(entry);
    assertThat(extracted.text()).isEqualTo("Hello feed");
  }

  @Test
  void fallsBackToDescription() {
    SyndEntryImpl entry = new SyndEntryImpl();
    SyndContentImpl description = new SyndContentImpl();
    description.setValue("Description body");
    entry.setDescription(description);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted) extractor.extract(entry);
    assertThat(extracted.text()).isEqualTo("Description body");
  }

  @Test
  void choosesLongestNonBlankCandidate() {
    SyndEntryImpl entry = new SyndEntryImpl();
    SyndContentImpl content = new SyndContentImpl();
    content.setValue("Short");
    entry.getContents().add(content);
    SyndContentImpl description = new SyndContentImpl();
    description.setValue("Longer description body");
    entry.setDescription(description);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted) extractor.extract(entry);
    assertThat(extracted.text()).isEqualTo("Longer description body");
  }

  @Test
  void ignoresNullAndBlankCandidatesAsNoContent() {
    SyndEntryImpl entry = new SyndEntryImpl();
    SyndContentImpl content = new SyndContentImpl();
    content.setValue(" ");
    entry.getContents().add(content);

    TextExtractionOutcome outcome = extractor.extract(entry);
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.NoContent.class);
    assertThat(((TextExtractionOutcome.NoContent) outcome).reason())
        .isEqualTo(NoContentReason.FEED_CONTENT_MISSING);
  }

  @Test
  void treatsXhtmlAsHtml() {
    SyndEntryImpl entry = new SyndEntryImpl();
    SyndContentImpl content = new SyndContentImpl();
    content.setType("application/xhtml+xml");
    content.setValue("<div>Rendered <em>body</em></div>");
    entry.getContents().add(content);

    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted) extractor.extract(entry);
    assertThat(extracted.text()).isEqualTo("Rendered body");
  }
}
