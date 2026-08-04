package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContentFullTextWriterMetadataTest {

  private final ContentFullTextRepository repository = mock(ContentFullTextRepository.class);
  private final ContentFullTextWriter writer = new ContentFullTextWriter(repository);

  @Test
  void persistsNonAutopagerizeMetadataWithoutPaginationColumns() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    TextExtractionOutcome.Extracted extracted =
        new TextExtractionOutcome.Extracted(
            "body",
            ExtractionDecision.of(ExtractionSource.READABILITY),
            Optional.empty(),
            Optional.of("https://final.example/a"));

    assertThat(writer.saveIfAbsent(header, FullTextMethod.HTTP_READABILITY, extracted))
        .isEqualTo(ContentFullTextWriteOutcome.INSERTED);

    ContentFullText saved = capture();
    assertThat(saved.extractionMethod()).isEqualTo("http_readability");
    assertThat(saved.extractionStatus()).isEqualTo("success");
    assertThat(saved.errorMessage()).isNull();
    assertThat(saved.sourceKind()).isEqualTo("readability");
    assertThat(saved.extractedUrl()).isEqualTo("https://final.example/a");
    assertThat(saved.autopagerizeDatasetId()).isNull();
    assertThat(saved.autopagerizeRuleOrdinal()).isNull();
    assertThat(saved.paginationPageCount()).isNull();
    assertThat(saved.paginationStopReason()).isNull();
    assertThat(saved.paginationComplete()).isNull();
  }

  @Test
  void persistsAutopagerizeNoRuleMetadata() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    PaginationMetadata meta =
        new PaginationMetadata(
            11L,
            "b".repeat(64),
            1,
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1,
            PaginationStopReason.NO_MATCHING_RULE,
            true,
            List.of(
                new PaginationPageTrace(
                    1,
                    URI.create("https://example.com/a"),
                    URI.create("https://final.example/a"),
                    100)),
            Optional.empty(),
            List.of(
                new PageTextContribution(1, ExtractionSource.BODY_TEXT, Optional.empty(), "body")));

    TextExtractionOutcome.Extracted extracted =
        new TextExtractionOutcome.Extracted(
            "body",
            ExtractionDecision.of(ExtractionSource.BODY_TEXT),
            Optional.of(meta),
            Optional.of("https://final.example/a"));

    writer.saveIfAbsent(header, FullTextMethod.HTTP_AUTOPAGERIZE, extracted);

    ContentFullText saved = capture();
    assertThat(saved.extractionMethod()).isEqualTo("http_autopagerize");
    assertThat(saved.sourceKind()).isEqualTo("body_text");
    assertThat(saved.autopagerizeDatasetId()).isEqualTo(11L);
    assertThat(saved.autopagerizeRuleOrdinal()).isNull();
    assertThat(saved.paginationPageCount()).isEqualTo(1);
    assertThat(saved.paginationStopReason()).isEqualTo("NO_MATCHING_RULE");
    assertThat(saved.paginationComplete()).isTrue();
  }

  @Test
  void persistsMatchedMultiPageAutopagerizeMetadata() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    PaginationMetadata meta =
        new PaginationMetadata(
            22L,
            "c".repeat(64),
            2,
            false,
            Optional.of(5),
            Optional.of("Site"),
            Optional.of("^https://example"),
            Optional.of("//a[@rel='next']"),
            Optional.of("//div"),
            3,
            PaginationStopReason.NO_NEXT_LINK,
            true,
            List.of(
                new PaginationPageTrace(
                    1,
                    URI.create("https://example.com/1"),
                    URI.create("https://example.com/1"),
                    10),
                new PaginationPageTrace(
                    2,
                    URI.create("https://example.com/2"),
                    URI.create("https://example.com/2"),
                    20),
                new PaginationPageTrace(
                    3,
                    URI.create("https://example.com/3"),
                    URI.create("https://example.com/3"),
                    30)),
            Optional.empty(),
            List.of());

    TextExtractionOutcome.Extracted extracted =
        new TextExtractionOutcome.Extracted(
            "p1\n\np2\n\np3",
            ExtractionDecision.of(ExtractionSource.PAGE_ELEMENT),
            Optional.of(meta),
            Optional.of("https://example.com/1"));

    writer.saveIfAbsent(header, FullTextMethod.PLAYWRIGHT_AUTOPAGERIZE, extracted);

    ContentFullText saved = capture();
    assertThat(saved.extractionMethod()).isEqualTo("playwright_autopagerize");
    assertThat(saved.sourceKind()).isEqualTo("page_element");
    assertThat(saved.autopagerizeDatasetId()).isEqualTo(22L);
    assertThat(saved.autopagerizeRuleOrdinal()).isEqualTo(5);
    assertThat(saved.paginationPageCount()).isEqualTo(3);
    assertThat(saved.paginationStopReason()).isEqualTo("NO_NEXT_LINK");
    assertThat(saved.paginationComplete()).isTrue();
    assertThat(saved.extractedUrl()).isEqualTo("https://example.com/1");
  }

  @Test
  void doesNotInsertBlankText() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    TextExtractionOutcome.Extracted extracted =
        // cannot construct blank Extracted; blank path is the feed convenience API
        null;
    assertThat(writer.saveIfAbsent(header, "  ")).isEqualTo(ContentFullTextWriteOutcome.NO_CONTENT);
    verify(repository, never()).insertIfAbsent(any());
    assertThat(extracted).isNull();
  }

  private ContentFullText capture() {
    ArgumentCaptor<ContentFullText> captor = ArgumentCaptor.forClass(ContentFullText.class);
    verify(repository).insertIfAbsent(captor.capture());
    return captor.getValue();
  }
}
