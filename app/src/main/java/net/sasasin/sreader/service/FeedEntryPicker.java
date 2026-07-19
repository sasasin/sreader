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
    return pick(feed, selection, true);
  }

  public Optional<SyndEntry> pick(
      SyndFeed feed, FeedEntrySelection selection, boolean requireLink) {
    if (feed == null || feed.getEntries() == null || feed.getEntries().isEmpty()) {
      return Optional.empty();
    }
    List<SyndEntry> entries = feed.getEntries();

    return switch (selection.kind()) {
      case FIRST -> pickFirst(entries, requireLink);
      case LATEST -> pickLatest(entries, requireLink);
      case INDEX ->
          pickByIndex(entries, selection.index() != null ? selection.index() : 0, requireLink);
      case TITLE_REGEX ->
          pickByTitleRegex(
              entries, selection.titleRegex() != null ? selection.titleRegex() : "", requireLink);
      case URL_REGEX ->
          pickByUrlRegex(entries, selection.urlRegex() != null ? selection.urlRegex() : "");
    };
  }

  private Optional<SyndEntry> pickFirst(List<SyndEntry> entries, boolean requireLink) {
    return entries.stream().filter(e -> canPick(e, requireLink)).findFirst();
  }

  private Optional<SyndEntry> pickLatest(List<SyndEntry> entries, boolean requireLink) {
    Optional<SyndEntry> dated =
        entries.stream()
            .filter(e -> canPick(e, requireLink))
            .filter(e -> effectiveDate(e) != null)
            .max(Comparator.comparing(this::effectiveDate));
    if (dated.isPresent()) {
      return dated;
    }
    return pickFirst(entries, requireLink);
  }

  private Optional<SyndEntry> pickByIndex(List<SyndEntry> entries, int index, boolean requireLink) {
    if (index < 0 || index >= entries.size()) {
      return Optional.empty();
    }
    SyndEntry e = entries.get(index);
    return canPick(e, requireLink) ? Optional.of(e) : Optional.empty();
  }

  private Optional<SyndEntry> pickByTitleRegex(
      List<SyndEntry> entries, String regex, boolean requireLink) {
    return pickByRegex(entries, regex, requireLink, SyndEntry::getTitle);
  }

  private Optional<SyndEntry> pickByUrlRegex(List<SyndEntry> entries, String regex) {
    return pickByRegex(entries, regex, true, SyndEntry::getLink);
  }

  private Optional<SyndEntry> pickByRegex(
      List<SyndEntry> entries,
      String regex,
      boolean requireLink,
      java.util.function.Function<SyndEntry, String> matchTarget) {
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
        .filter(e -> canPick(e, requireLink))
        .filter(
            e -> {
              String target = matchTarget.apply(e);
              return target != null && pattern.matcher(target).find();
            })
        .findFirst();
  }

  private boolean canPick(SyndEntry e, boolean requireLink) {
    return e != null && (!requireLink || hasLink(e));
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
