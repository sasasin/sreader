package net.sasasin.sreader.domain;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * How to pick one entry from a feed. Each subtype holds only the values that selection kind needs,
 * so invalid combinations cannot be constructed.
 */
public sealed interface FeedEntrySelection
    permits FeedEntrySelection.First,
        FeedEntrySelection.Latest,
        FeedEntrySelection.ByIndex,
        FeedEntrySelection.ByTitleRegex,
        FeedEntrySelection.ByUrlRegex {

  record First() implements FeedEntrySelection {}

  record Latest() implements FeedEntrySelection {}

  record ByIndex(int index) implements FeedEntrySelection {
    public ByIndex {
      if (index < 0) {
        throw new IllegalArgumentException("entry index must be >= 0, but was: " + index);
      }
    }
  }

  record ByTitleRegex(String regex) implements FeedEntrySelection {
    public ByTitleRegex {
      regex = requireCompilableRegex(regex, "title regex");
    }
  }

  record ByUrlRegex(String regex) implements FeedEntrySelection {
    public ByUrlRegex {
      regex = requireCompilableRegex(regex, "url regex");
    }
  }

  static FeedEntrySelection first() {
    return new First();
  }

  static FeedEntrySelection latest() {
    return new Latest();
  }

  static FeedEntrySelection index(int idx) {
    return new ByIndex(idx);
  }

  static FeedEntrySelection titleRegex(String regex) {
    return new ByTitleRegex(regex);
  }

  static FeedEntrySelection urlRegex(String regex) {
    return new ByUrlRegex(regex);
  }

  private static String requireCompilableRegex(String regex, String label) {
    Objects.requireNonNull(regex, label + " must not be null");
    if (regex.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    try {
      Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException(label + " is not a valid Java regex: " + regex, e);
    }
    return regex;
  }
}
