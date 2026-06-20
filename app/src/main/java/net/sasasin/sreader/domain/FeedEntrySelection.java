package net.sasasin.sreader.domain;

public record FeedEntrySelection(Kind kind, Integer index, String titleRegex, String urlRegex) {
  public enum Kind {
    FIRST,
    LATEST,
    INDEX,
    TITLE_REGEX,
    URL_REGEX,
  }

  public static FeedEntrySelection first() {
    return new FeedEntrySelection(Kind.FIRST, null, null, null);
  }

  public static FeedEntrySelection latest() {
    return new FeedEntrySelection(Kind.LATEST, null, null, null);
  }

  public static FeedEntrySelection index(int idx) {
    return new FeedEntrySelection(Kind.INDEX, idx, null, null);
  }

  public static FeedEntrySelection titleRegex(String regex) {
    return new FeedEntrySelection(Kind.TITLE_REGEX, null, regex, null);
  }

  public static FeedEntrySelection urlRegex(String regex) {
    return new FeedEntrySelection(Kind.URL_REGEX, null, null, regex);
  }
}
