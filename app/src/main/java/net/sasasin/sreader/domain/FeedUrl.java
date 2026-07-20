package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Subscribed or unsubscribed feed URL. Active feeds cannot carry unsubscribe metadata; unsubscribed
 * feeds require a reason. {@link FullTextMethod} is always present.
 */
public record FeedUrl(
    String id,
    String url,
    FeedStatus status,
    UnsubscribeReason unsubscribeReason,
    OffsetDateTime unsubscribedAt,
    String note,
    FullTextMethod fullTextMethod) {

  public FeedUrl {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(url, "url must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(fullTextMethod, "fullTextMethod must not be null");
    if (status == FeedStatus.ACTIVE) {
      if (unsubscribeReason != null || unsubscribedAt != null || note != null) {
        throw new IllegalArgumentException(
            "active feed must not have unsubscribeReason, unsubscribedAt, or note");
      }
    } else if (status == FeedStatus.UNSUBSCRIBED) {
      if (unsubscribeReason == null) {
        throw new IllegalArgumentException("unsubscribed feed requires unsubscribeReason");
      }
    }
  }

  public FeedUrl(String id, String url) {
    this(id, url, FeedStatus.ACTIVE, null, null, null, FullTextMethod.HTTP);
  }
}
