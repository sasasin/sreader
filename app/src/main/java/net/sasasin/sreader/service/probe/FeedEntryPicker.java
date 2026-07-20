package net.sasasin.sreader.service.probe;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import net.sasasin.sreader.domain.FeedEntrySelection;
import org.springframework.stereotype.Service;

@Service
public class FeedEntryPicker {

  public Optional<SyndEntry> pick(SyndFeed feed, FeedEntrySelection selection) {
    return pick(feed, selection, true);
  }

  public Optional<SyndEntry> pick(
      SyndFeed feed, FeedEntrySelection selection, boolean requireLink) {
    Objects.requireNonNull(selection, "selection must not be null");
    if (feed == null || feed.getEntries() == null || feed.getEntries().isEmpty()) {
      return Optional.empty();
    }
    List<SyndEntry> entries = feed.getEntries();

    return switch (selection) {
      case FeedEntrySelection.First() -> pickFirst(entries, requireLink);
      case FeedEntrySelection.Latest() -> pickLatest(entries, requireLink);
      case FeedEntrySelection.ByIndex(int index) -> pickByIndex(entries, index, requireLink);
      case FeedEntrySelection.ByTitleRegex(String regex) ->
          pickByTitleRegex(entries, regex, requireLink);
      case FeedEntrySelection.ByUrlRegex(String regex) -> pickByUrlRegex(entries, regex);
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
    Pattern pattern = Pattern.compile(regex);
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
