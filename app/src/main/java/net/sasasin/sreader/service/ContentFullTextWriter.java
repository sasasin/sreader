package net.sasasin.sreader.service;

import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import org.springframework.stereotype.Service;

@Service
public class ContentFullTextWriter {

  private final ContentFullTextRepository repository;

  public ContentFullTextWriter(ContentFullTextRepository repository) {
    this.repository = repository;
  }

  public boolean saveIfAbsent(ContentHeader header, String fullText) {
    if (fullText == null || fullText.isBlank()) {
      return false;
    }
    return repository.insertIfAbsent(
        new ContentFullText(HashIds.md5(header.canonicalUrl()), header.id(), fullText));
  }
}
