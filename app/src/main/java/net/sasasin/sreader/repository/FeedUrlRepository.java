package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.FEED_URL;

import java.time.OffsetDateTime;
import java.util.List;
import net.sasasin.sreader.domain.FeedUrl;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class FeedUrlRepository {

	private final DSLContext dsl;

	public FeedUrlRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public List<FeedUrl> findAll() {
		return dsl.select(FEED_URL.ID, FEED_URL.URL)
				.from(FEED_URL)
				.orderBy(FEED_URL.URL)
				.fetch(record -> new FeedUrl(record.get(FEED_URL.ID), record.get(FEED_URL.URL)));
	}

	public boolean insertIfAbsent(String id, String url) {
		return dsl.insertInto(FEED_URL)
				.set(FEED_URL.ID, id)
				.set(FEED_URL.URL, url)
				.set(FEED_URL.CREATED_AT, OffsetDateTime.now())
				.set(FEED_URL.UPDATED_AT, OffsetDateTime.now())
				.onConflict(FEED_URL.ID)
				.doNothing()
				.execute() == 1;
	}
}
