package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.CONTENT_TEXT_FILE_EXPORT;
import static net.sasasin.sreader.jooq.Tables.FEED_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class RepositoryIntegrationTest {

  @Autowired DSLContext dsl;

  @Autowired FeedUrlRepository feedUrlRepository;

  @Autowired ContentHeaderRepository contentHeaderRepository;

  @Autowired ContentFullTextRepository contentFullTextRepository;

  @Autowired ContentTextFileExportRepository contentTextFileExportRepository;

  @BeforeEach
  void cleanTables() {
    dsl.deleteFrom(CONTENT_TEXT_FILE_EXPORT).execute();
    dsl.deleteFrom(CONTENT_FULL_TEXT).execute();
    dsl.deleteFrom(CONTENT_HEADER).execute();
    dsl.deleteFrom(FEED_URL).execute();
  }

  @Test
  void registersFeedUrlAndSuppressesDuplicates() {
    assertThat(
            feedUrlRepository.insertIfAbsent(
                "feed0000000000000000000000000001", "https://example.test/rss.xml"))
        .isTrue();
    assertThat(
            feedUrlRepository.insertIfAbsent(
                "feed0000000000000000000000000001", "https://example.test/rss.xml"))
        .isFalse();
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
            FullTextMethod.HTTP));
    feedUrlRepository.insertFromImport(
        new FeedUrl(
            "feed0000000000000000000000000004",
            "https://example.test/old.xml",
            FeedStatus.UNSUBSCRIBED.value(),
            "site_closed",
            null,
            "closed",
            FullTextMethod.HTTP));

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
                .fullTextMethod())
        .isEqualTo(FullTextMethod.HTTP);

    assertThatThrownBy(
            () ->
                dsl.insertInto(FEED_URL)
                    .set(FEED_URL.ID, "feed0000000000000000000000000006")
                    .set(FEED_URL.URL, "https://example.test/bad-method.xml")
                    .set(FEED_URL.FULL_TEXT_METHOD, "bad_method")
                    .execute())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void savesHeaderAndFullTextOnce() {
    feedUrlRepository.insertIfAbsent(
        "feed0000000000000000000000000002", "https://example.test/feed.xml");
    ContentHeader header =
        new ContentHeader(
            "head0000000000000000000000000001",
            "feed0000000000000000000000000002",
            "https://example.test/article/1",
            "Article 1",
            null);

    assertThat(contentHeaderRepository.insertOrRefreshFetchUrl(header)).isTrue();
    assertThat(contentHeaderRepository.insertOrRefreshFetchUrl(header)).isFalse();
    assertThat(contentHeaderRepository.findWithoutFullText(10)).hasSize(1);

    ContentFullText fullText =
        new ContentFullText(
            "text0000000000000000000000000001", "head0000000000000000000000000001", "Hello body");
    assertThat(contentFullTextRepository.insertIfAbsent(fullText)).isTrue();
    assertThat(contentFullTextRepository.insertIfAbsent(fullText)).isFalse();
    assertThat(contentHeaderRepository.findWithoutFullText(10)).isEmpty();
  }

  @Test
  void pendingUrlExtractionIncludesConfiguredMethodAndFeedText() {
    feedUrlRepository.insertFromImport(
        new FeedUrl(
            "feed0000000000000000000000000010",
            "https://example.test/http.xml",
            FeedStatus.ACTIVE.value(),
            null,
            null,
            null,
            FullTextMethod.HTTP));
    feedUrlRepository.insertFromImport(
        new FeedUrl(
            "feed0000000000000000000000000011",
            "https://example.test/feed.xml",
            FeedStatus.ACTIVE.value(),
            null,
            null,
            null,
            FullTextMethod.FEED));
    feedUrlRepository.insertFromImport(
        new FeedUrl(
            "feed0000000000000000000000000012",
            "https://example.test/playwright.xml",
            FeedStatus.ACTIVE.value(),
            null,
            null,
            null,
            FullTextMethod.PLAYWRIGHT));

    contentHeaderRepository.insertOrRefreshFetchUrl(
        new ContentHeader(
            "head0000000000000000000000000010",
            "feed0000000000000000000000000010",
            "https://example.test/http",
            "HTTP",
            null));
    contentHeaderRepository.insertOrRefreshFetchUrl(
        new ContentHeader(
            "head0000000000000000000000000011",
            "feed0000000000000000000000000011",
            "https://example.test/feed",
            "FEED",
            null));
    contentHeaderRepository.insertOrRefreshFetchUrl(
        new ContentHeader(
            "head0000000000000000000000000012",
            "feed0000000000000000000000000012",
            "https://example.test/playwright",
            "PLAYWRIGHT",
            null));

    assertThat(contentHeaderRepository.findWithoutFullTextForUrlExtraction(10))
        .extracting(target -> target.header().fetchUrl())
        .containsExactly(
            "https://example.test/http",
            "https://example.test/feed",
            "https://example.test/playwright");
  }

  @Test
  void contentTextFileExportFindsOnlyUnexportedNonBlankFullText() {
    feedUrlRepository.insertIfAbsent(
        "feed0000000000000000000000000020", "https://example.test/export.xml");
    insertHeaderAndFullText(
        "0123456789abcdef0123456789abcdef",
        "11111111111111111111111111111111",
        "https://example.test/export/1",
        "Export 1",
        "Body 1");
    insertHeaderAndFullText(
        "22222222222222222222222222222222",
        "33333333333333333333333333333333",
        "https://example.test/export/2",
        "Export 2",
        "Body 2");
    insertHeaderAndFullText(
        "44444444444444444444444444444444",
        "55555555555555555555555555555555",
        "https://example.test/export/blank",
        "Blank",
        "   ");

    assertThat(
            contentTextFileExportRepository.insertExported(
                "22222222222222222222222222222222",
                "33333333333333333333333333333333",
                "22222222222222222222222222222222.txt",
                10,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        .isTrue();

    assertThat(contentTextFileExportRepository.findUnexported(10))
        .extracting(target -> target.contentHeaderId())
        .containsExactly("0123456789abcdef0123456789abcdef");
  }

  @Test
  void contentTextFileExportInsertIsIdempotent() {
    feedUrlRepository.insertIfAbsent(
        "feed0000000000000000000000000020", "https://example.test/export-once.xml");
    insertHeaderAndFullText(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "https://example.test/export-once/1",
        "Export once",
        "Body");

    assertThat(
            contentTextFileExportRepository.insertExported(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.txt",
                9,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"))
        .isTrue();
    assertThat(
            contentTextFileExportRepository.insertExported(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.txt",
                9,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"))
        .isFalse();
  }

  private void insertHeaderAndFullText(
      String headerId, String fullTextId, String url, String title, String fullText) {
    contentHeaderRepository.insertOrRefreshFetchUrl(
        new ContentHeader(headerId, "feed0000000000000000000000000020", url, title, null));
    contentFullTextRepository.insertIfAbsent(new ContentFullText(fullTextId, headerId, fullText));
  }
}
