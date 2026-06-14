package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.FEED_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class RepositoryIntegrationTest {

    @Autowired
    DSLContext dsl;

    @Autowired
    FeedUrlRepository feedUrlRepository;

    @Autowired
    ContentHeaderRepository contentHeaderRepository;

    @Autowired
    ContentFullTextRepository contentFullTextRepository;

    @BeforeEach
    void cleanTables() {
        dsl.deleteFrom(CONTENT_FULL_TEXT).execute();
        dsl.deleteFrom(CONTENT_HEADER).execute();
        dsl.deleteFrom(FEED_URL).execute();
    }

    @Test
    void registersFeedUrlAndSuppressesDuplicates() {
        assertThat(
            feedUrlRepository.insertIfAbsent(
                "feed0000000000000000000000000001",
                "https://example.test/rss.xml"
            )
        ).isTrue();
        assertThat(
            feedUrlRepository.insertIfAbsent(
                "feed0000000000000000000000000001",
                "https://example.test/rss.xml"
            )
        ).isFalse();
        assertThat(feedUrlRepository.findActiveForReading()).hasSize(1);
    }

    @Test
    void activeReaderQueryExcludesUnsubscribedFeeds() {
        feedUrlRepository.insertFromImport(
            new FeedUrl(
                "feed0000000000000000000000000003",
                "https://example.test/active.xml",
                FeedStatus.ACTIVE.value(),
                null,
                null,
                null,
                "http"
            )
        );
        feedUrlRepository.insertFromImport(
            new FeedUrl(
                "feed0000000000000000000000000004",
                "https://example.test/old.xml",
                FeedStatus.UNSUBSCRIBED.value(),
                "site_closed",
                null,
                "closed",
                "http"
            )
        );

        assertThat(feedUrlRepository.findActiveForReading())
            .extracting(FeedUrl::url)
            .containsExactly("https://example.test/active.xml");
    }

    @Test
    void fullTextMethodMigrationDefaultAndConstraintAreApplied() {
        dsl.insertInto(FEED_URL)
            .set(FEED_URL.ID, "feed0000000000000000000000000005")
            .set(FEED_URL.URL, "https://example.test/default-method.xml")
            .execute();

        assertThat(
            feedUrlRepository
                .findByUrl("https://example.test/default-method.xml")
                .orElseThrow()
                .fullTextMethod()
        ).isEqualTo("http");

        assertThatThrownBy(() ->
            dsl.insertInto(FEED_URL)
                .set(FEED_URL.ID, "feed0000000000000000000000000006")
                .set(FEED_URL.URL, "https://example.test/bad-method.xml")
                .set(FEED_URL.FULL_TEXT_METHOD, "bad_method")
                .execute()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesHeaderAndFullTextOnce() {
        feedUrlRepository.insertIfAbsent(
            "feed0000000000000000000000000002",
            "https://example.test/feed.xml"
        );
        ContentHeader header = new ContentHeader(
            "head0000000000000000000000000001",
            "feed0000000000000000000000000002",
            "https://example.test/article/1",
            "Article 1",
            null
        );

        assertThat(contentHeaderRepository.insertIfAbsent(header)).isTrue();
        assertThat(contentHeaderRepository.insertIfAbsent(header)).isFalse();
        assertThat(contentHeaderRepository.findWithoutFullText(10)).hasSize(1);

        ContentFullText fullText = new ContentFullText(
            "text0000000000000000000000000001",
            "head0000000000000000000000000001",
            "Hello body"
        );
        assertThat(contentFullTextRepository.insertIfAbsent(fullText)).isTrue();
        assertThat(
            contentFullTextRepository.insertIfAbsent(fullText)
        ).isFalse();
        assertThat(contentHeaderRepository.findWithoutFullText(10)).isEmpty();
    }
}
