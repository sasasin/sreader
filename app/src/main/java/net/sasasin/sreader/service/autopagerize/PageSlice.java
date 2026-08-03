package net.sasasin.sreader.service.autopagerize;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * One page in a matched AutoPagerize chain (or a single unmatched first page for diagnostics).
 * pageElement fields are empty when no rule was applied.
 */
public record PageSlice(
    int pageNumber,
    URI requestedUri,
    URI finalUri,
    String html,
    String pageElementOuterHtml,
    String pageElementText,
    Optional<URI> nextUri,
    Optional<String> pageElementContentHash,
    long byteSize) {

  public PageSlice {
    if (pageNumber < 1) {
      throw new IllegalArgumentException("pageNumber must be >= 1");
    }
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    Objects.requireNonNull(finalUri, "finalUri must not be null");
    Objects.requireNonNull(html, "html must not be null");
    Objects.requireNonNull(pageElementOuterHtml, "pageElementOuterHtml must not be null");
    Objects.requireNonNull(pageElementText, "pageElementText must not be null");
    Objects.requireNonNull(nextUri, "nextUri must not be null");
    Objects.requireNonNull(pageElementContentHash, "pageElementContentHash must not be null");
    if (byteSize < 0) {
      throw new IllegalArgumentException("byteSize must not be negative");
    }
  }

  public static PageSlice withoutPageElement(int pageNumber, PageSnapshot snapshot) {
    return new PageSlice(
        pageNumber,
        snapshot.requestedUri(),
        snapshot.finalUri(),
        snapshot.html(),
        "",
        "",
        Optional.empty(),
        Optional.empty(),
        snapshot.byteSize());
  }
}
