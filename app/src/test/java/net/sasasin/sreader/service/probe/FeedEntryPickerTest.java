package net.sasasin.sreader.service.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import java.util.Arrays;
import java.util.Date;
import net.sasasin.sreader.domain.FeedEntrySelection;
import org.junit.jupiter.api.Test;

class FeedEntryPickerTest {

  private final FeedEntryPicker picker = new FeedEntryPicker();

  @Test
  void returnsEmptyForNullFeed() {
    assertThat(picker.pick(null, FeedEntrySelection.first())).isEmpty();
  }

  @Test
  void rejectsNullSelection() {
    assertThatThrownBy(() -> picker.pick(feed(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("selection");
  }

  @Test
  void returnsEmptyForNullOrEmptyEntries() {
    SyndFeed nullEntries = mock(SyndFeed.class);
    when(nullEntries.getEntries()).thenReturn(null);
    assertThat(picker.pick(nullEntries, FeedEntrySelection.first())).isEmpty();
    assertThat(picker.pick(feed(), FeedEntrySelection.first())).isEmpty();
  }

  @Test
  void firstSkipsNullAndBlankLinksWhenRequired() {
    assertThat(
            picker.pick(
                feed(null, entry(null, "none"), entry(" ", "blank"), entry("https://ok", "ok")),
                FeedEntrySelection.first()))
        .contains(entry("https://ok", "ok"));
  }

  @Test
  void firstCanReturnLinklessEntryWhenNotRequired() {
    SyndEntry linkless = entry(null, "feed body");
    assertThat(picker.pick(feed(linkless), FeedEntrySelection.first(), false))
        .containsSame(linkless);
  }

  @Test
  void firstReturnsEmptyWhenNothingIsEligible() {
    assertThat(picker.pick(feed(null, entry(" ", "blank")), FeedEntrySelection.first())).isEmpty();
  }

  @Test
  void latestSelectsGreatestPublishedDate() {
    SyndEntry old = dated("https://old", "old", 1000, 9000);
    SyndEntry latest = dated("https://new", "new", 2000, 100);
    assertThat(picker.pick(feed(old, latest), FeedEntrySelection.latest())).containsSame(latest);
  }

  @Test
  void latestUsesUpdatedDateWhenPublishedDateIsAbsent() {
    SyndEntry old = dated("https://old", "old", null, 1000);
    SyndEntry latest = dated("https://new", "new", null, 2000);
    assertThat(picker.pick(feed(old, latest), FeedEntrySelection.latest())).containsSame(latest);
  }

  @Test
  void latestFallsBackToFirstWhenNoEligibleEntryHasADate() {
    SyndEntry first = entry("https://first", "first");
    assertThat(
            picker.pick(
                feed(first, entry("https://second", "second")), FeedEntrySelection.latest()))
        .containsSame(first);
  }

  @Test
  void latestIgnoresUndatedAndIneligibleEntries() {
    SyndEntry dated = dated("https://dated", "dated", 1000, null);
    SyndEntry noLink = dated(null, "no link", 9000, null);
    assertThat(
            picker.pick(
                feed(entry("https://undated", "undated"), noLink, dated),
                FeedEntrySelection.latest()))
        .containsSame(dated);
  }

  @Test
  void indexRejectsOutOfRangeValues() {
    SyndFeed feed = feed(entry("https://zero", "zero"));
    assertThat(picker.pick(feed, FeedEntrySelection.index(1))).isEmpty();
    assertThat(picker.pick(feed, FeedEntrySelection.index(2))).isEmpty();
  }

  @Test
  void indexReturnsEligibleEntryAndRejectsNullOrLinklessEntryWhenRequired() {
    SyndEntry valid = entry("https://one", "one");
    SyndFeed feed = feed(null, valid, entry(null, "linkless"));
    assertThat(picker.pick(feed, FeedEntrySelection.index(1))).containsSame(valid);
    assertThat(picker.pick(feed, FeedEntrySelection.index(0))).isEmpty();
    assertThat(picker.pick(feed, FeedEntrySelection.index(2))).isEmpty();
  }

  @Test
  void indexZeroCanReturnLinklessEntryWhenNotRequired() {
    SyndEntry linkless = entry(null, "linkless");
    assertThat(picker.pick(feed(linkless), FeedEntrySelection.index(0), false))
        .containsSame(linkless);
  }

  @Test
  void titleRegexUsesPartialMatchAndCanSelectLinklessEntryWhenAllowed() {
    SyndEntry entry = entry(null, "Alpha article");
    assertThat(picker.pick(feed(entry), FeedEntrySelection.titleRegex("pha"), false))
        .containsSame(entry);
  }

  @Test
  void titleRegexSkipsLinklessEntryWhenRequiredAndNullTitles() {
    SyndEntry matchingWithoutLink = entry(null, "match");
    assertThat(
            picker.pick(
                feed(entry("https://null-title", null), matchingWithoutLink),
                FeedEntrySelection.titleRegex("match")))
        .isEmpty();
  }

  @Test
  void titleRegexReturnsEmptyWhenNoMatch() {
    SyndFeed feed = feed(entry("https://one", "title"));
    assertThat(picker.pick(feed, FeedEntrySelection.titleRegex("absent"))).isEmpty();
  }

  @Test
  void urlRegexUsesPartialMatchButAlwaysRequiresANonblankLink() {
    SyndEntry match = entry("https://host/path/beta", "title");
    assertThat(
            picker.pick(
                feed(entry(null, "beta"), entry(" ", "beta"), match),
                FeedEntrySelection.urlRegex("beta"),
                false))
        .containsSame(match);
  }

  @Test
  void urlRegexReturnsEmptyWhenNoMatch() {
    SyndFeed feed = feed(entry("https://one", "title"));
    assertThat(picker.pick(feed, FeedEntrySelection.urlRegex("absent"))).isEmpty();
  }

  private SyndEntry dated(String link, String title, Integer published, Integer updated) {
    SyndEntry entry = entry(link, title);
    if (published != null) {
      entry.setPublishedDate(new Date(published));
    }
    if (updated != null) {
      entry.setUpdatedDate(new Date(updated));
    }
    return entry;
  }

  private SyndEntry entry(String link, String title) {
    SyndEntryImpl entry = new SyndEntryImpl();
    entry.setLink(link);
    entry.setTitle(title);
    return entry;
  }

  private SyndFeed feed(SyndEntry... entries) {
    SyndFeedImpl feed = new SyndFeedImpl();
    feed.setEntries(Arrays.asList(entries));
    return feed;
  }
}
