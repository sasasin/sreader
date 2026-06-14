package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;

public record FeedUrl(
		String id,
		String url,
		String status,
		String unsubscribeReason,
		OffsetDateTime unsubscribedAt,
		String note) {

	public FeedUrl(String id, String url) {
		this(id, url, "active", null, null, null);
	}
}
