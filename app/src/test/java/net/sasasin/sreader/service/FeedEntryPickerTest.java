package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedEntrySelection;
import org.junit.jupiter.api.Test;

class FeedEntryPickerTest {

  private final FeedEntryPicker picker = new FeedEntryPicker();

  @Test
  void firstReturnsFirstWithLink() {
    SyndFeed feed =
        feedWith(
            entries(entry(null, "no"), entry("https://e/1", "t1"), entry("https://e/2", "t2")));
    Optional<SyndEntry> p = picker.pick(feed, FeedEntrySelection.first());
    assertThat(p).isPresent();
    assertThat(p.get().getLink()).isEqualTo("https://e/1");
  }

  @Test
  void latestSortsByDateDesc() {
    Date d1 = new Date(1000);
    Date d2 = new Date(2000);
    SyndEntry e1 = entry("https://e/1", "t1");
    e1.setPublishedDate(d1);
    SyndEntry e2 = entry("https://e/2", "t2");
    e2.setPublishedDate(d2);
    SyndFeed feed = feedWith(entries(e1, e2));
    Optional<SyndEntry> p = picker.pick(feed, FeedEntrySelection.latest());
    assertThat(p).isPresent();
    assertThat(p.get().getLink()).isEqualTo("https://e/2");
  }

  @Test
  void indexZeroBased() {
    SyndFeed feed = feedWith(entries(entry("https://e/0", "0"), entry("https://e/1", "1")));
    assertThat(picker.pick(feed, FeedEntrySelection.index(1))).isPresent();
    assertThat(picker.pick(feed, FeedEntrySelection.index(5))).isEmpty();
  }

  @Test
  void titleRegex() {
    SyndFeed feed =
        feedWith(entries(entry("https://e/1", "Alpha article"), entry("https://e/2", "Beta")));
    Optional<SyndEntry> p = picker.pick(feed, FeedEntrySelection.titleRegex("Beta"));
    assertThat(p).isPresent();
    assertThat(p.get().getLink()).isEqualTo("https://e/2");
  }

  @Test
  void urlRegex() {
    SyndFeed feed = feedWith(entries(entry("https://e/alpha", "t"), entry("https://e/beta", "t")));
    Optional<SyndEntry> p = picker.pick(feed, FeedEntrySelection.urlRegex("beta"));
    assertThat(p).isPresent();
    assertThat(p.get().getLink()).isEqualTo("https://e/beta");
  }

  @Test
  void skipsEntriesWithoutLink() {
    SyndFeed feed = feedWith(entries(entry(null, "no"), entry("https://ok", "yes")));
    assertThat(picker.pick(feed, FeedEntrySelection.first()).get().getLink())
        .isEqualTo("https://ok");
  }

  @Test
  void canPickEntryWithoutLinkWhenLinkIsNotRequired() {
    SyndFeed feed = feedWith(entries(entry(null, "feed body only"), entry("https://ok", "yes")));
    assertThat(picker.pick(feed, FeedEntrySelection.first(), false)).isPresent();
    assertThat(picker.pick(feed, FeedEntrySelection.first(), false).get().getTitle())
        .isEqualTo("feed body only");
  }

  @Test
  void emptyWhenNoMatch() {
    SyndFeed feed = feedWith(entries(entry("https://e/1", "t")));
    assertThat(picker.pick(feed, FeedEntrySelection.titleRegex("nope"))).isEmpty();
  }

  private List<SyndEntry> entries(SyndEntry... es) {
    return Arrays.asList(es);
  }

  private SyndEntry entry(String link, String title) {
    SyndEntryImpl e = new SyndEntryImpl();
    e.setLink(link);
    e.setTitle(title);
    return e;
  }

  private SyndFeed feedWith(List<SyndEntry> es) {
    SyndFeedImpl f = new SyndFeedImpl();
    f.setEntries(es);
    return f;
  }
}
