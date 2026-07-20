package net.sasasin.sreader.service.extraction;

import net.dankito.readability4j.Article;

/** Parses HTML into a Readability article for text extraction. */
@FunctionalInterface
interface ReadabilityParser {

  Article parse(String url, String html);
}
