package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.FEED_URL;

import java.time.OffsetDateTime;
import java.util.List;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ContentHeaderRepository {

  private final DSLContext dsl;

  public ContentHeaderRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public boolean insertIfAbsent(ContentHeader header) {
    OffsetDateTime now = OffsetDateTime.now();
    return dsl.insertInto(CONTENT_HEADER)
            .set(CONTENT_HEADER.ID, header.id())
            .set(CONTENT_HEADER.FEED_URL_ID, header.feedUrlId())
            .set(CONTENT_HEADER.URL, header.url())
            .set(CONTENT_HEADER.TITLE, header.title())
            .set(CONTENT_HEADER.PUBLISHED_AT, header.publishedAt())
            .set(CONTENT_HEADER.FEED_TEXT, header.feedText())
            .set(CONTENT_HEADER.CREATED_AT, now)
            .set(CONTENT_HEADER.UPDATED_AT, now)
            .onConflict(CONTENT_HEADER.ID)
            .doNothing()
            .execute()
        == 1;
  }

  public List<ContentHeader> findWithoutFullText(int limit) {
    return dsl.select(
            CONTENT_HEADER.ID,
            CONTENT_HEADER.FEED_URL_ID,
            CONTENT_HEADER.URL,
            CONTENT_HEADER.TITLE,
            CONTENT_HEADER.PUBLISHED_AT,
            CONTENT_HEADER.FEED_TEXT)
        .from(CONTENT_HEADER)
        .leftJoin(CONTENT_FULL_TEXT)
        .on(CONTENT_HEADER.ID.eq(CONTENT_FULL_TEXT.CONTENT_HEADER_ID))
        .where(CONTENT_FULL_TEXT.ID.isNull())
        .orderBy(CONTENT_HEADER.CREATED_AT.asc())
        .limit(limit)
        .fetch(
            record ->
                new ContentHeader(
                    record.get(CONTENT_HEADER.ID),
                    record.get(CONTENT_HEADER.FEED_URL_ID),
                    record.get(CONTENT_HEADER.URL),
                    record.get(CONTENT_HEADER.TITLE),
                    record.get(CONTENT_HEADER.PUBLISHED_AT),
                    record.get(CONTENT_HEADER.FEED_TEXT)));
  }

  public List<PendingFullTextTarget> findWithoutFullTextForUrlExtraction(int limit) {
    return dsl.select(
            CONTENT_HEADER.ID,
            CONTENT_HEADER.FEED_URL_ID,
            CONTENT_HEADER.URL,
            CONTENT_HEADER.TITLE,
            CONTENT_HEADER.PUBLISHED_AT,
            CONTENT_HEADER.FEED_TEXT,
            FEED_URL.FULL_TEXT_METHOD)
        .from(CONTENT_HEADER)
        .join(FEED_URL)
        .on(CONTENT_HEADER.FEED_URL_ID.eq(FEED_URL.ID))
        .leftJoin(CONTENT_FULL_TEXT)
        .on(CONTENT_HEADER.ID.eq(CONTENT_FULL_TEXT.CONTENT_HEADER_ID))
        .where(CONTENT_FULL_TEXT.ID.isNull())
        .orderBy(CONTENT_HEADER.CREATED_AT.asc())
        .limit(limit)
        .fetch(
            record ->
                new PendingFullTextTarget(
                    new ContentHeader(
                        record.get(CONTENT_HEADER.ID),
                        record.get(CONTENT_HEADER.FEED_URL_ID),
                        record.get(CONTENT_HEADER.URL),
                        record.get(CONTENT_HEADER.TITLE),
                        record.get(CONTENT_HEADER.PUBLISHED_AT),
                        record.get(CONTENT_HEADER.FEED_TEXT)),
                    toFullTextMethod(record.get(FEED_URL.FULL_TEXT_METHOD))));
  }

  private FullTextMethod toFullTextMethod(String value) {
    return value == null ? FullTextMethod.HTTP : FullTextMethod.fromValue(value);
  }
}
