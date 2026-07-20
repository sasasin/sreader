package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.CONTENT_TEXT_FILE_EXPORT;
import static net.sasasin.sreader.jooq.Tables.FEED_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import net.sasasin.sreader.domain.ContentCanonicalizationFullText;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.domain.ContentCanonicalizationSurvivor;
import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;
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

  @Autowired ContentCanonicalizationMaintenanceRepository canonicalizationMaintenanceRepository;

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
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.HTTP));
    feedUrlRepository.insertFromImport(
        new FeedUrl(
            "feed0000000000000000000000000004",
            "https://example.test/old.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.SITE_CLOSED,
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
        .isEqualTo(FullTextMethod.defaultMethod());
    assertThat(FullTextMethod.defaultMethod().value()).isEqualTo("http");

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
  void allCatalogFullTextMethodsRoundTripThroughFeedUrlRepository() {
    int index = 0;
    for (FullTextMethod method : FullTextMethod.values()) {
      String id = String.format("feed000000000000000000000000%04d", 100 + index);
      String url = "https://example.test/method-" + method.value() + ".xml";
      feedUrlRepository.insertFromImport(
          new FeedUrl(id, url, FeedStatus.ACTIVE, null, null, null, method));
      FeedUrl loaded = feedUrlRepository.findByUrl(url).orElseThrow();
      assertThat(loaded.fullTextMethod()).isEqualTo(method);
      assertThat(loaded.fullTextMethod().value()).isEqualTo(method.value());
      index++;
    }
  }

  @Test
  void pendingUrlExtractionMapsEveryCatalogMethod() {
    int index = 0;
    for (FullTextMethod method : FullTextMethod.values()) {
      String feedId = String.format("feed000000000000000000000000%04d", 200 + index);
      String headerId = String.format("head000000000000000000000000%04d", 200 + index);
      String feedUrl = "https://example.test/pending-method-" + method.value() + ".xml";
      String articleUrl = "https://example.test/pending-article-" + method.value();
      feedUrlRepository.insertFromImport(
          new FeedUrl(feedId, feedUrl, FeedStatus.ACTIVE, null, null, null, method));
      contentHeaderRepository.insertOrRefreshFetchUrl(
          new ContentHeader(headerId, feedId, articleUrl, "title", null));
      index++;
    }

    assertThat(contentHeaderRepository.findWithoutFullTextForUrlExtraction(20))
        .extracting(target -> target.method())
        .containsExactlyInAnyOrder(FullTextMethod.values());
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

    assertThat(contentHeaderRepository.insertOrRefreshFetchUrl(header))
        .isEqualTo(net.sasasin.sreader.service.feed.ingestion.ContentHeaderUpsertOutcome.INSERTED);
    assertThat(contentHeaderRepository.insertOrRefreshFetchUrl(header))
        .isEqualTo(
            net.sasasin.sreader.service.feed.ingestion.ContentHeaderUpsertOutcome
                .EXISTING_REFRESHED);
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
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.HTTP));
    feedUrlRepository.insertFromImport(
        new FeedUrl(
            "feed0000000000000000000000000011",
            "https://example.test/feed.xml",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.FEED));
    feedUrlRepository.insertFromImport(
        new FeedUrl(
            "feed0000000000000000000000000012",
            "https://example.test/playwright.xml",
            FeedStatus.ACTIVE,
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

  @Test
  void canonicalizationCandidateAndGroupQueriesRespectHostAndCursor() {
    insertMaintenanceFeed();
    insertMaintenanceHeader(
        "00000000000000000000000000000001", "https://canonicalization.test/n/article?gs=a");
    insertMaintenanceHeader(
        "00000000000000000000000000000002", "https://canonicalization.test/n/article?gs=b");
    insertMaintenanceHeader(
        "00000000000000000000000000000003", "https://other.test/n/article?gs=a");

    assertThat(
            canonicalizationMaintenanceRepository.findCandidateCanonicalUrls(
                "canonicalization.test", null, 10))
        .containsExactly(
            "https://canonicalization.test/n/article?gs=a",
            "https://canonicalization.test/n/article?gs=b");
    assertThat(
            canonicalizationMaintenanceRepository.findCandidateCanonicalUrls(
                "canonicalization.test", "https://canonicalization.test/n/article?gs=a", 10))
        .containsExactly("https://canonicalization.test/n/article?gs=b");

    ContentCanonicalizationGroup group =
        canonicalizationMaintenanceRepository.loadGroup("https://canonicalization.test/n/article");
    assertThat(group.members()).hasSize(2);
    assertThat(group.exportHistoryRows()).isZero();
  }

  @Test
  void canonicalizationMergeMovesSelectedFullTextAndDeletesOldRecords() {
    insertMaintenanceFeed();
    String oldId = "00000000000000000000000000000011";
    String canonicalId = "00000000000000000000000000000012";
    String oldTextId = "10000000000000000000000000000011";
    String canonicalUrl = "https://canonicalization.test/n/article-merge";
    insertMaintenanceHeader(oldId, canonicalUrl + "?gs=a");
    insertMaintenanceHeader(canonicalId, canonicalUrl);
    dsl.insertInto(CONTENT_FULL_TEXT)
        .set(CONTENT_FULL_TEXT.ID, oldTextId)
        .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, oldId)
        .set(CONTENT_FULL_TEXT.FULL_TEXT, "long retained body")
        .execute();
    dsl.insertInto(CONTENT_TEXT_FILE_EXPORT)
        .set(CONTENT_TEXT_FILE_EXPORT.CONTENT_HEADER_ID, oldId)
        .set(CONTENT_TEXT_FILE_EXPORT.CONTENT_FULL_TEXT_ID, oldTextId)
        .set(CONTENT_TEXT_FILE_EXPORT.RELATIVE_PATH, oldId + ".txt")
        .set(CONTENT_TEXT_FILE_EXPORT.FILE_SIZE_BYTES, 1L)
        .set(
            CONTENT_TEXT_FILE_EXPORT.FILE_SHA256,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        .execute();
    ContentCanonicalizationGroup group =
        canonicalizationMaintenanceRepository.loadGroup(canonicalUrl);
    var selectedMember =
        group.members().stream()
            .filter(member -> oldId.equals(member.id()))
            .findFirst()
            .orElseThrow();
    assertThat(selectedMember.fullText()).isPresent();
    ContentCanonicalizationFullText selected = selectedMember.fullText().orElseThrow();
    ContentCanonicalizationSurvivor values =
        new ContentCanonicalizationSurvivor(
            canonicalId,
            "feed0000000000000000000000000099",
            "https://source.test/first",
            "https://fetch.test/newest",
            canonicalUrl,
            "merged title",
            null,
            "feed body",
            selectedMember.createdAt());

    ContentCanonicalizationMaintenanceRepository.MergeCounts counts =
        canonicalizationMaintenanceRepository.merge(
            new ContentCanonicalizationPlan(group, values, Optional.of(selected), false));

    assertThat(counts.deletedHeaders()).isEqualTo(1);
    assertThat(counts.deletedFullTexts()).isZero();
    assertThat(counts.deletedExportHistories()).isEqualTo(1);
    assertThat(dsl.fetchCount(CONTENT_HEADER)).isEqualTo(1);
    assertThat(dsl.fetchCount(CONTENT_FULL_TEXT)).isEqualTo(1);
    assertThat(
            dsl.select(CONTENT_FULL_TEXT.CONTENT_HEADER_ID)
                .from(CONTENT_FULL_TEXT)
                .fetchOne(0, String.class))
        .isEqualTo(canonicalId);
    assertThat(dsl.fetchCount(CONTENT_TEXT_FILE_EXPORT)).isZero();
  }

  @Test
  void canonicalizationMergeDeletesAllFullTextsWhenNoneIsSelected() {
    insertMaintenanceFeed();
    String oldId = "00000000000000000000000000000021";
    String survivorId = "00000000000000000000000000000022";
    String url = "https://canonicalization.test/n/article-empty";
    insertMaintenanceHeader(oldId, url + "?gs=a");
    dsl.insertInto(CONTENT_FULL_TEXT)
        .set(CONTENT_FULL_TEXT.ID, "20000000000000000000000000000021")
        .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, oldId)
        .set(CONTENT_FULL_TEXT.FULL_TEXT, "")
        .execute();
    ContentCanonicalizationGroup group = canonicalizationMaintenanceRepository.loadGroup(url);
    var member = group.members().getFirst();
    assertThat(member.fullText()).isPresent();
    ContentCanonicalizationSurvivor values =
        new ContentCanonicalizationSurvivor(
            survivorId,
            member.feedUrlId(),
            member.sourceUrl(),
            member.fetchUrl(),
            url,
            member.title(),
            null,
            member.feedText(),
            member.createdAt());

    ContentCanonicalizationMaintenanceRepository.MergeCounts counts =
        canonicalizationMaintenanceRepository.merge(
            new ContentCanonicalizationPlan(group, values, Optional.empty(), false));

    assertThat(counts.deletedHeaders()).isEqualTo(1);
    assertThat(counts.deletedFullTexts()).isEqualTo(1);
    assertThat(dsl.fetchCount(CONTENT_HEADER)).isEqualTo(1);
    assertThat(dsl.fetchCount(CONTENT_FULL_TEXT)).isZero();
  }

  private void insertMaintenanceFeed() {
    feedUrlRepository.insertIfAbsent(
        "feed0000000000000000000000000099", "https://canonicalization.test/feed.xml");
  }

  private void insertMaintenanceHeader(String id, String canonicalUrl) {
    dsl.insertInto(CONTENT_HEADER)
        .set(CONTENT_HEADER.ID, id)
        .set(CONTENT_HEADER.FEED_URL_ID, "feed0000000000000000000000000099")
        .set(CONTENT_HEADER.SOURCE_URL, canonicalUrl)
        .set(CONTENT_HEADER.FETCH_URL, canonicalUrl)
        .set(CONTENT_HEADER.CANONICAL_URL, canonicalUrl)
        .set(CONTENT_HEADER.TITLE, "title")
        .execute();
  }

  private void insertHeaderAndFullText(
      String headerId, String fullTextId, String url, String title, String fullText) {
    contentHeaderRepository.insertOrRefreshFetchUrl(
        new ContentHeader(headerId, "feed0000000000000000000000000020", url, title, null));
    contentFullTextRepository.insertIfAbsent(new ContentFullText(fullTextId, headerId, fullText));
  }
}
