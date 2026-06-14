package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.FEED_URL;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class FeedUrlRepository {

    private final DSLContext dsl;

    public FeedUrlRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<FeedUrl> findActiveForReading() {
        return dsl
            .select(
                FEED_URL.ID,
                FEED_URL.URL,
                FEED_URL.STATUS,
                FEED_URL.UNSUBSCRIBE_REASON,
                FEED_URL.UNSUBSCRIBED_AT,
                FEED_URL.NOTE,
                FEED_URL.FULL_TEXT_METHOD
            )
            .from(FEED_URL)
            .where(FEED_URL.STATUS.eq(FeedStatus.ACTIVE.value()))
            .orderBy(FEED_URL.URL)
            .fetch(record ->
                new FeedUrl(
                    record.get(FEED_URL.ID),
                    record.get(FEED_URL.URL),
                    record.get(FEED_URL.STATUS),
                    record.get(FEED_URL.UNSUBSCRIBE_REASON),
                    record.get(FEED_URL.UNSUBSCRIBED_AT),
                    record.get(FEED_URL.NOTE),
                    record.get(FEED_URL.FULL_TEXT_METHOD)
                )
            );
    }

    public List<FeedUrl> findAllForExport(boolean activeOnly) {
        Condition condition = activeOnly
            ? FEED_URL.STATUS.eq(FeedStatus.ACTIVE.value())
            : DSL.trueCondition();
        return dsl
            .select(
                FEED_URL.ID,
                FEED_URL.URL,
                FEED_URL.STATUS,
                FEED_URL.UNSUBSCRIBE_REASON,
                FEED_URL.UNSUBSCRIBED_AT,
                FEED_URL.NOTE,
                FEED_URL.FULL_TEXT_METHOD
            )
            .from(FEED_URL)
            .where(condition)
            .orderBy(FEED_URL.URL)
            .fetch(record ->
                new FeedUrl(
                    record.get(FEED_URL.ID),
                    record.get(FEED_URL.URL),
                    record.get(FEED_URL.STATUS),
                    record.get(FEED_URL.UNSUBSCRIBE_REASON),
                    record.get(FEED_URL.UNSUBSCRIBED_AT),
                    record.get(FEED_URL.NOTE),
                    record.get(FEED_URL.FULL_TEXT_METHOD)
                )
            );
    }

    public Optional<FeedUrl> findByUrl(String url) {
        return dsl
            .select(
                FEED_URL.ID,
                FEED_URL.URL,
                FEED_URL.STATUS,
                FEED_URL.UNSUBSCRIBE_REASON,
                FEED_URL.UNSUBSCRIBED_AT,
                FEED_URL.NOTE,
                FEED_URL.FULL_TEXT_METHOD
            )
            .from(FEED_URL)
            .where(FEED_URL.URL.eq(url))
            .fetchOptional(record ->
                new FeedUrl(
                    record.get(FEED_URL.ID),
                    record.get(FEED_URL.URL),
                    record.get(FEED_URL.STATUS),
                    record.get(FEED_URL.UNSUBSCRIBE_REASON),
                    record.get(FEED_URL.UNSUBSCRIBED_AT),
                    record.get(FEED_URL.NOTE),
                    record.get(FEED_URL.FULL_TEXT_METHOD)
                )
            );
    }

    public boolean insertIfAbsent(String id, String url) {
        return (
            dsl
                .insertInto(FEED_URL)
                .set(FEED_URL.ID, id)
                .set(FEED_URL.URL, url)
                .set(FEED_URL.STATUS, FeedStatus.ACTIVE.value())
                .set(FEED_URL.FULL_TEXT_METHOD, FullTextMethod.HTTP.value())
                .set(FEED_URL.CREATED_AT, OffsetDateTime.now())
                .set(FEED_URL.UPDATED_AT, OffsetDateTime.now())
                .onConflict(FEED_URL.ID)
                .doNothing()
                .execute() == 1
        );
    }

    public void insertFromImport(FeedUrl feedUrl) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(FEED_URL)
            .set(FEED_URL.ID, feedUrl.id())
            .set(FEED_URL.URL, feedUrl.url())
            .set(FEED_URL.STATUS, feedUrl.status())
            .set(FEED_URL.UNSUBSCRIBE_REASON, feedUrl.unsubscribeReason())
            .set(FEED_URL.UNSUBSCRIBED_AT, feedUrl.unsubscribedAt())
            .set(FEED_URL.NOTE, feedUrl.note())
            .set(FEED_URL.FULL_TEXT_METHOD, feedUrl.fullTextMethod())
            .set(FEED_URL.CREATED_AT, now)
            .set(FEED_URL.UPDATED_AT, now)
            .execute();
    }

    public void unsubscribe(
        String url,
        String reason,
        OffsetDateTime unsubscribedAt,
        String note
    ) {
        dsl.update(FEED_URL)
            .set(FEED_URL.STATUS, FeedStatus.UNSUBSCRIBED.value())
            .set(FEED_URL.UNSUBSCRIBE_REASON, reason)
            .set(FEED_URL.UNSUBSCRIBED_AT, unsubscribedAt)
            .set(FEED_URL.NOTE, note)
            .set(FEED_URL.UPDATED_AT, OffsetDateTime.now())
            .where(FEED_URL.URL.eq(url))
            .execute();
    }

    public void updateUnsubscribedMetadata(
        String url,
        String reason,
        OffsetDateTime unsubscribedAt,
        String note
    ) {
        dsl.update(FEED_URL)
            .set(FEED_URL.UNSUBSCRIBE_REASON, reason)
            .set(FEED_URL.UNSUBSCRIBED_AT, unsubscribedAt)
            .set(FEED_URL.NOTE, note)
            .set(FEED_URL.UPDATED_AT, OffsetDateTime.now())
            .where(FEED_URL.URL.eq(url))
            .execute();
    }

    public void resubscribe(String url) {
        dsl.update(FEED_URL)
            .set(FEED_URL.STATUS, FeedStatus.ACTIVE.value())
            .set(FEED_URL.UNSUBSCRIBE_REASON, (String) null)
            .set(FEED_URL.UNSUBSCRIBED_AT, (OffsetDateTime) null)
            .set(FEED_URL.NOTE, (String) null)
            .set(FEED_URL.UPDATED_AT, OffsetDateTime.now())
            .where(FEED_URL.URL.eq(url))
            .execute();
    }

    public void updateFullTextMethod(String url, String fullTextMethod) {
        dsl.update(FEED_URL)
            .set(FEED_URL.FULL_TEXT_METHOD, fullTextMethod)
            .set(FEED_URL.UPDATED_AT, OffsetDateTime.now())
            .where(FEED_URL.URL.eq(url))
            .execute();
    }
}
