package net.sasasin.sreader.service.autopagerize;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Result of applying nextLink / pageElement XPath to one page snapshot. */
public record PageAnalysis(
    String pageElementOuterHtml,
    String pageElementText,
    String pageElementContentHash,
    Optional<URI> nextUri,
    Optional<NextLinkIssue> nextLinkIssue) {

  public PageAnalysis {
    Objects.requireNonNull(pageElementOuterHtml, "pageElementOuterHtml must not be null");
    Objects.requireNonNull(pageElementText, "pageElementText must not be null");
    Objects.requireNonNull(pageElementContentHash, "pageElementContentHash must not be null");
    Objects.requireNonNull(nextUri, "nextUri must not be null");
    Objects.requireNonNull(nextLinkIssue, "nextLinkIssue must not be null");
  }

  public enum NextLinkIssue {
    NONE,
    MISSING,
    INVALID_URI,
    UNSUPPORTED_SCHEME,
    USERINFO_REJECTED
  }
}
