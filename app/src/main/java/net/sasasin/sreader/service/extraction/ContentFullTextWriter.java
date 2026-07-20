package net.sasasin.sreader.service.extraction;

import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import net.sasasin.sreader.service.article.HashIds;
import org.springframework.stereotype.Service;

@Service
public class ContentFullTextWriter {

  private final ContentFullTextRepository repository;

  public ContentFullTextWriter(ContentFullTextRepository repository) {
    this.repository = repository;
  }

  public ContentFullTextWriteOutcome saveIfAbsent(ContentHeader header, String fullText) {
    if (fullText == null || fullText.isBlank()) {
      return ContentFullTextWriteOutcome.NO_CONTENT;
    }
    boolean inserted =
        repository.insertIfAbsent(
            new ContentFullText(HashIds.md5(header.canonicalUrl()), header.id(), fullText));
    return inserted
        ? ContentFullTextWriteOutcome.INSERTED
        : ContentFullTextWriteOutcome.ALREADY_EXISTS;
  }
}
