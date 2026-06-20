package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.sasasin.sreader.domain.FeedEntrySelection;
import org.springframework.stereotype.Service;

@Service
public class FeedEntryPicker {

  public Optional<SyndEntry> pick(SyndFeed feed, FeedEntrySelection selection) {
    if (feed == null || feed.getEntries() == null || feed.getEntries().isEmpty()) {
      return Optional.empty();
    }
    List<SyndEntry> entries = feed.getEntries();

    return switch (selection.kind()) {
      case FIRST -> pickFirstWithLink(entries);
      case LATEST -> pickLatestWithLink(entries);
      case INDEX -> pickByIndex(entries, selection.index() != null ? selection.index() : 0);
      case TITLE_REGEX ->
          pickByRegex(entries, selection.titleRegex() != null ? selection.titleRegex() : "", true);
      case URL_REGEX ->
          pickByRegex(entries, selection.urlRegex() != null ? selection.urlRegex() : "", false);
    };
  }

  private Optional<SyndEntry> pickFirstWithLink(List<SyndEntry> entries) {
    return entries.stream().filter(this::hasLink).findFirst();
  }

  private Optional<SyndEntry> pickLatestWithLink(List<SyndEntry> entries) {
    return entries.stream()
        .filter(this::hasLink)
        .max(
            Comparator.comparing(
                this::effectiveDate, Comparator.nullsFirst(Comparator.naturalOrder())));
  }

  private Optional<SyndEntry> pickByIndex(List<SyndEntry> entries, int index) {
    if (index < 0 || index >= entries.size()) {
      return Optional.empty();
    }
    SyndEntry e = entries.get(index);
    return hasLink(e) ? Optional.of(e) : Optional.empty();
  }

  private Optional<SyndEntry> pickByRegex(List<SyndEntry> entries, String regex, boolean useTitle) {
    if (regex == null || regex.isBlank()) {
      return Optional.empty();
    }
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      return Optional.empty();
    }
    return entries.stream()
        .filter(this::hasLink)
        .filter(
            e -> {
              String target = useTitle ? e.getTitle() : e.getLink();
              return target != null && pattern.matcher(target).find();
            })
        .findFirst();
  }

  private boolean hasLink(SyndEntry e) {
    return e != null && e.getLink() != null && !e.getLink().isBlank();
  }

  private Date effectiveDate(SyndEntry e) {
    if (e.getPublishedDate() != null) {
      return e.getPublishedDate();
    }
    return e.getUpdatedDate();
  }
}
