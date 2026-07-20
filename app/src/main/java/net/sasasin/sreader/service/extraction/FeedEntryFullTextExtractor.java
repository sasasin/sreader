package net.sasasin.sreader.service.extraction;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Service
public class FeedEntryFullTextExtractor {

  public TextExtractionOutcome extract(SyndEntry entry) {
    List<String> candidates = new ArrayList<>();
    for (SyndContent content : entry.getContents()) {
      normalize(content).ifPresent(candidates::add);
    }
    normalize(entry.getDescription()).ifPresent(candidates::add);

    return candidates.stream()
        .map(String::trim)
        .filter(text -> !text.isBlank())
        .max(Comparator.comparingInt(String::length))
        .<TextExtractionOutcome>map(
            text ->
                new TextExtractionOutcome.Extracted(
                    text, ExtractionDecision.of(ExtractionSource.FEED)))
        .orElseGet(
            () ->
                new TextExtractionOutcome.NoContent(
                    NoContentReason.FEED_CONTENT_MISSING,
                    ExtractionDecision.of(ExtractionSource.FEED)));
  }

  private Optional<String> normalize(SyndContent content) {
    if (content == null || content.getValue() == null || content.getValue().isBlank()) {
      return Optional.empty();
    }
    String value = content.getValue();
    if (isHtmlLike(content.getType(), value)) {
      return Optional.of(Jsoup.parseBodyFragment(value).body().text());
    }
    return Optional.of(value.strip());
  }

  private boolean isHtmlLike(String type, String value) {
    if (type != null) {
      String lower = type.toLowerCase(Locale.ROOT);
      if (lower.contains("html") || lower.contains("xhtml")) {
        return true;
      }
    }
    return value.matches("(?is).*<\\s*(p|div|br|article|section|span|a|img|ul|ol|li|h[1-6])\\b.*");
  }
}
