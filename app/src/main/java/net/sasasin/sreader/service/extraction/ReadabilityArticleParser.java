package net.sasasin.sreader.service.extraction;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.extended.Readability4JExtended;
import org.springframework.stereotype.Component;

/** Production adapter for Readability4JExtended. */
@Component
final class ReadabilityArticleParser implements ReadabilityParser {

  @Override
  public Article parse(String url, String html) {
    return new Readability4JExtended(url, html).parse();
  }
}
