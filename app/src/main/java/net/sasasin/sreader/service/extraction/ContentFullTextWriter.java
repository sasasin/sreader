package net.sasasin.sreader.service.extraction;

import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import net.sasasin.sreader.service.article.HashIds;
import org.springframework.stereotype.Service;

@Service
public class ContentFullTextWriter {

  private final ContentFullTextRepository repository;

  public ContentFullTextWriter(ContentFullTextRepository repository) {
    this.repository = repository;
  }

  /**
   * Feed-entry convenience: method {@code feed}, source {@code feed}, extracted URL = header
   * canonical URL. AutoPagerize columns remain null.
   */
  public ContentFullTextWriteOutcome saveIfAbsent(ContentHeader header, String fullText) {
    if (fullText == null || fullText.isBlank()) {
      return ContentFullTextWriteOutcome.NO_CONTENT;
    }
    TextExtractionOutcome.Extracted extracted =
        new TextExtractionOutcome.Extracted(
            fullText,
            ExtractionDecision.of(ExtractionSource.FEED),
            Optional.empty(),
            Optional.of(header.canonicalUrl()));
    return saveIfAbsent(header, FullTextMethod.FEED, extracted);
  }

  /**
   * Persist a successful extraction with method, source kind, extracted URL, and optional
   * AutoPagerize columns. Never inserts failure or partial rows. Callers must pass non-blank
   * extracted text ({@link TextExtractionOutcome.Extracted} already enforces this).
   */
  public ContentFullTextWriteOutcome saveIfAbsent(
      ContentHeader header, FullTextMethod method, TextExtractionOutcome.Extracted extracted) {
    Objects.requireNonNull(header, "header must not be null");
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(extracted, "extracted must not be null");

    String id = HashIds.md5(header.canonicalUrl());
    String extractedUrl = extracted.extractedUrl().orElse(header.fetchUrl());
    String sourceKind = extracted.decision().source().wireValue();

    ContentFullText row;
    Optional<PaginationMetadata> pagination = extracted.pagination();
    if (pagination.isEmpty()) {
      row =
          ContentFullText.success(
              id, header.id(), extracted.text(), method.value(), sourceKind, extractedUrl);
    } else {
      PaginationMetadata meta = pagination.get();
      // Success rows always store page_count >= 1 (schema check).
      int pageCount = meta.pageCount();
      if (pageCount < 1) {
        pageCount = 1;
      }
      row =
          ContentFullText.successAutopagerize(
              id,
              header.id(),
              extracted.text(),
              method.value(),
              sourceKind,
              extractedUrl,
              meta.datasetId(),
              meta.ruleOrdinal(),
              pageCount,
              meta.stopReason().name(),
              meta.complete());
    }

    boolean inserted = repository.insertIfAbsent(row);
    if (inserted) {
      return ContentFullTextWriteOutcome.INSERTED;
    }
    return ContentFullTextWriteOutcome.ALREADY_EXISTS;
  }
}
