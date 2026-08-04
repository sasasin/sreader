package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_DATASET;
import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_RULE;
import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_RULE_REJECTION;
import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_STATE;
import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.FEED_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.sasasin.sreader.domain.AutoPagerizeActiveState;
import net.sasasin.sreader.domain.AutoPagerizeDataset;
import net.sasasin.sreader.domain.AutoPagerizeDatasetCreate;
import net.sasasin.sreader.domain.AutoPagerizeDatasetSummary;
import net.sasasin.sreader.domain.AutoPagerizeFormats;
import net.sasasin.sreader.domain.AutoPagerizeRule;
import net.sasasin.sreader.domain.AutoPagerizeRuleCounts;
import net.sasasin.sreader.domain.AutoPagerizeRuleRejection;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class AutoPagerizeRepositoryIntegrationTest {

  private static final String SHA_A =
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String SHA_B =
      "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final String SHA_C =
      "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

  @Autowired DSLContext dsl;

  @Autowired AutoPagerizeDatasetRepository datasetRepository;

  @Autowired AutoPagerizeRuleRepository ruleRepository;

  @Autowired AutoPagerizeStateRepository stateRepository;

  @BeforeEach
  void cleanTables() {
    dsl.deleteFrom(CONTENT_FULL_TEXT).execute();
    dsl.deleteFrom(CONTENT_HEADER).execute();
    dsl.deleteFrom(FEED_URL).execute();
    stateRepository.clearActiveDataset();
    dsl.deleteFrom(AUTOPAGERIZE_RULE).execute();
    dsl.deleteFrom(AUTOPAGERIZE_RULE_REJECTION).execute();
    dsl.deleteFrom(AUTOPAGERIZE_DATASET).execute();
  }

  @Test
  void stateSingletonExistsWithNullActiveDataset() {
    assertThat(dsl.fetchCount(AUTOPAGERIZE_STATE)).isEqualTo(1);
    AutoPagerizeActiveState state = stateRepository.findActiveState();
    assertThat(state.activeDatasetId()).isNull();
    assertThat(state.activatedAt()).isNull();
    assertThat(stateRepository.findActiveDatasetId()).isEmpty();
  }

  @Test
  void savesAndLoadsDatasetRulesAndRejections() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "items_all.json",
                "file:///tmp/items_all.json",
                SHA_A,
                1,
                2,
                1,
                1,
                "{\"source\":\"test\"}"));

    ruleRepository.insertRules(
        List.of(
            new AutoPagerizeRule(
                datasetId,
                0,
                0,
                "86244",
                "http://wedata.net/items/86244",
                "Example Site",
                "author",
                null,
                null,
                "^https://example\\.com/",
                "//a[@rel='next']",
                "//div[@class='body']",
                null,
                "https://example.com/a/1",
                "{\"name\":\"Example Site\"}")));
    ruleRepository.insertRejections(
        List.of(
            new AutoPagerizeRuleRejection(
                datasetId,
                1,
                "Broken",
                "{\"name\":\"Broken\"}",
                "[{\"code\":\"missing_url\",\"message\":\"data.url is required\"}]")));

    AutoPagerizeDataset loaded = datasetRepository.findById(datasetId).orElseThrow();
    assertThat(loaded.format()).isEqualTo(AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL);
    assertThat(loaded.sourceFilename()).isEqualTo("items_all.json");
    assertThat(loaded.sourceSha256()).isEqualTo(SHA_A);
    assertThat(loaded.inputItemCount()).isEqualTo(2);
    assertThat(loaded.acceptedRuleCount()).isEqualTo(1);
    assertThat(loaded.rejectedRuleCount()).isEqualTo(1);
    assertThat(loaded.metadataJson()).contains("source");

    assertThat(
            datasetRepository.findByIdentity(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL, SHA_A, 1))
        .isPresent()
        .get()
        .extracting(AutoPagerizeDataset::id)
        .isEqualTo(datasetId);
    assertThat(datasetRepository.existsById(datasetId)).isTrue();
    assertThat(datasetRepository.existsById(datasetId + 9999)).isFalse();

    List<AutoPagerizeRule> rules =
        ruleRepository.findRulesByDatasetIdOrderedByMatchOrder(datasetId);
    assertThat(rules).hasSize(1);
    assertThat(rules.getFirst().name()).isEqualTo("Example Site");
    assertThat(rules.getFirst().urlPattern()).isEqualTo("^https://example\\.com/");

    List<AutoPagerizeRuleRejection> rejections =
        ruleRepository.findRejectionsByDatasetIdOrderedByOrdinal(datasetId);
    assertThat(rejections).hasSize(1);
    assertThat(rejections.getFirst().errorsJson()).contains("missing_url");

    AutoPagerizeRuleCounts counts = ruleRepository.countByDatasetId(datasetId);
    assertThat(counts.acceptedRuleCount()).isEqualTo(1);
    assertThat(counts.rejectedRuleCount()).isEqualTo(1);
    assertThat(counts.total()).isEqualTo(2);
  }

  @Test
  void rejectionErrorsMustBeJsonArrayOrObject() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL, null, null, SHA_A, 1, 1, 0, 1));

    assertThatThrownBy(
            () ->
                dsl.insertInto(
                        AUTOPAGERIZE_RULE_REJECTION,
                        AUTOPAGERIZE_RULE_REJECTION.DATASET_ID,
                        AUTOPAGERIZE_RULE_REJECTION.ORDINAL,
                        AUTOPAGERIZE_RULE_REJECTION.RAW_ITEM,
                        AUTOPAGERIZE_RULE_REJECTION.ERRORS)
                    .values(
                        datasetId,
                        0,
                        JSONB.valueOf("{\"name\":\"bad\"}"),
                        JSONB.valueOf("\"not-structured\""))
                    .execute())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rulesAreReturnedInMatchOrder() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL, null, null, SHA_A, 1, 3, 3, 0));

    // Insert out of match_order so load order is proven independent of insert order.
    ruleRepository.insertRules(
        List.of(
            rule(datasetId, 2, 2, "third"),
            rule(datasetId, 0, 0, "first"),
            rule(datasetId, 1, 1, "second")));

    assertThat(ruleRepository.findRulesByDatasetIdOrderedByMatchOrder(datasetId))
        .extracting(AutoPagerizeRule::name)
        .containsExactly("first", "second", "third");
  }

  @Test
  void datasetIdentityUniqueConstraintIsEnforced() {
    AutoPagerizeDatasetCreate create =
        new AutoPagerizeDatasetCreate(
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            "items_all.json",
            null,
            SHA_A,
            1,
            0,
            0,
            0);
    datasetRepository.insert(create);
    assertThatThrownBy(() -> datasetRepository.insert(create))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void activePointerCanBeSwitched() {
    long first =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "a.json",
                null,
                SHA_A,
                1,
                0,
                0,
                0));
    long second =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "b.json",
                null,
                SHA_B,
                1,
                0,
                0,
                0));

    stateRepository.activateDataset(first);
    assertThat(stateRepository.findActiveDatasetId()).contains(first);
    assertThat(stateRepository.findActiveState().activatedAt()).isNotNull();

    AutoPagerizeActiveState locked = stateRepository.lockActiveState();
    assertThat(locked.activeDatasetId()).isEqualTo(first);

    stateRepository.activateDataset(second);
    assertThat(stateRepository.findActiveDatasetId()).contains(second);
  }

  @Test
  void activatingMissingDatasetIsRejected() {
    assertThatThrownBy(() -> stateRepository.activateDataset(9_999_999L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void activeOrReferencedDatasetCannotBeDeleted() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "active.json",
                null,
                SHA_A,
                1,
                1,
                1,
                0));
    ruleRepository.insertRules(List.of(rule(datasetId, 0, 0, "rule")));
    stateRepository.activateDataset(datasetId);

    assertThatThrownBy(
            () ->
                dsl.deleteFrom(AUTOPAGERIZE_DATASET)
                    .where(AUTOPAGERIZE_DATASET.ID.eq(datasetId))
                    .execute())
        .isInstanceOf(DataIntegrityViolationException.class);

    stateRepository.clearActiveDataset();
    insertContentReferencing(datasetId, 0);

    assertThatThrownBy(
            () ->
                dsl.deleteFrom(AUTOPAGERIZE_DATASET)
                    .where(AUTOPAGERIZE_DATASET.ID.eq(datasetId))
                    .execute())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingUnreferencedDatasetCascadesRulesAndRejections() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "cascade.json",
                null,
                SHA_A,
                1,
                2,
                1,
                1));
    ruleRepository.insertRules(List.of(rule(datasetId, 0, 0, "rule")));
    ruleRepository.insertRejections(
        List.of(
            new AutoPagerizeRuleRejection(
                datasetId, 1, "bad", "{\"name\":\"bad\"}", "[{\"code\":\"x\"}]")));

    int deleted =
        dsl.deleteFrom(AUTOPAGERIZE_DATASET).where(AUTOPAGERIZE_DATASET.ID.eq(datasetId)).execute();
    assertThat(deleted).isEqualTo(1);
    assertThat(dsl.fetchCount(AUTOPAGERIZE_RULE)).isZero();
    assertThat(dsl.fetchCount(AUTOPAGERIZE_RULE_REJECTION)).isZero();
    assertThat(datasetRepository.findById(datasetId)).isEmpty();
  }

  @Test
  void contentFullTextCompositeFkRejectsInvalidRuleOrdinal() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "fk.json",
                null,
                SHA_A,
                1,
                1,
                1,
                0));
    ruleRepository.insertRules(List.of(rule(datasetId, 0, 0, "only-rule")));
    insertFeedAndHeader("headapfk000000000000000000000001", "https://example.test/ap-fk-1");
    insertFeedAndHeader("headapfk000000000000000000000002", "https://example.test/ap-fk-2");
    insertFeedAndHeader("headapfk000000000000000000000003", "https://example.test/ap-fk-3");

    // Valid: dataset only (rule mismatch single-page case).
    dsl.insertInto(CONTENT_FULL_TEXT)
        .set(CONTENT_FULL_TEXT.ID, "textapfk000000000000000000000001")
        .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, "headapfk000000000000000000000001")
        .set(CONTENT_FULL_TEXT.FULL_TEXT, "body")
        .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_DATASET_ID, datasetId)
        .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_RULE_ORDINAL, (Integer) null)
        .set(CONTENT_FULL_TEXT.PAGINATION_PAGE_COUNT, 1)
        .set(CONTENT_FULL_TEXT.PAGINATION_COMPLETE, true)
        .execute();

    // Invalid rule ordinal under the dataset.
    assertThatThrownBy(
            () ->
                dsl.insertInto(CONTENT_FULL_TEXT)
                    .set(CONTENT_FULL_TEXT.ID, "textapfk000000000000000000000002")
                    .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, "headapfk000000000000000000000002")
                    .set(CONTENT_FULL_TEXT.FULL_TEXT, "other")
                    .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_DATASET_ID, datasetId)
                    .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_RULE_ORDINAL, 99)
                    .execute())
        .isInstanceOf(DataIntegrityViolationException.class);

    // Valid composite reference.
    dsl.insertInto(CONTENT_FULL_TEXT)
        .set(CONTENT_FULL_TEXT.ID, "textapfk000000000000000000000003")
        .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, "headapfk000000000000000000000003")
        .set(CONTENT_FULL_TEXT.FULL_TEXT, "paged")
        .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_DATASET_ID, datasetId)
        .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_RULE_ORDINAL, 0)
        .set(CONTENT_FULL_TEXT.PAGINATION_PAGE_COUNT, 2)
        .set(CONTENT_FULL_TEXT.PAGINATION_STOP_REASON, "no_next_link")
        .set(CONTENT_FULL_TEXT.PAGINATION_COMPLETE, true)
        .execute();
    assertThat(dsl.fetchCount(CONTENT_FULL_TEXT)).isEqualTo(2);
  }

  @Test
  void fullTextMethodCheckAcceptsFinalCatalogAndRejectsLegacyInfyAndUnknown() {
    String[] allowed = {
      "feed",
      "http",
      "http_readability",
      "http_autopagerize",
      "http_autopagerize_readability",
      "playwright",
      "playwright_readability",
      "playwright_autopagerize",
      "playwright_autopagerize_readability"
    };
    int index = 0;
    for (String method : allowed) {
      // char(32) primary key
      String id = String.format("feedap%026d", index);
      assertThat(id).hasSize(32);
      dsl.insertInto(FEED_URL)
          .set(FEED_URL.ID, id)
          .set(FEED_URL.URL, "https://example.test/ap-method-" + method + ".xml")
          .set(FEED_URL.FULL_TEXT_METHOD, method)
          .execute();
      index++;
    }
    assertThat(dsl.fetchCount(FEED_URL)).isEqualTo(allowed.length);

    assertThatThrownBy(
            () ->
                dsl.insertInto(FEED_URL)
                    .set(FEED_URL.ID, "feedapbad0000000000000000000001")
                    .set(FEED_URL.URL, "https://example.test/ap-method-bad.xml")
                    .set(FEED_URL.FULL_TEXT_METHOD, "not_a_method")
                    .execute())
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                dsl.insertInto(FEED_URL)
                    .set(FEED_URL.ID, "feedapinfy000000000000000000001")
                    .set(FEED_URL.URL, "https://example.test/ap-method-infy.xml")
                    .set(FEED_URL.FULL_TEXT_METHOD, "playwright_infy_scroll")
                    .execute())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void legacyInfyMethodsConvertToAutopagerizeMethods() {
    dsl.execute("ALTER TABLE feed_url DROP CONSTRAINT feed_url_full_text_method_check");
    try {
      dsl.insertInto(FEED_URL)
          .set(FEED_URL.ID, "feedmiginfy00000000000000000001")
          .set(FEED_URL.URL, "https://example.test/mig-infy.xml")
          .set(FEED_URL.FULL_TEXT_METHOD, "playwright_infy_scroll")
          .execute();
      dsl.insertInto(FEED_URL)
          .set(FEED_URL.ID, "feedmiginfy00000000000000000002")
          .set(FEED_URL.URL, "https://example.test/mig-infy-r.xml")
          .set(FEED_URL.FULL_TEXT_METHOD, "playwright_infy_scroll_readability")
          .execute();

      dsl.execute(
          "UPDATE feed_url SET full_text_method = 'playwright_autopagerize'"
              + " WHERE full_text_method = 'playwright_infy_scroll'");
      dsl.execute(
          "UPDATE feed_url SET full_text_method = 'playwright_autopagerize_readability'"
              + " WHERE full_text_method = 'playwright_infy_scroll_readability'");

      assertThat(
              dsl.select(FEED_URL.FULL_TEXT_METHOD)
                  .from(FEED_URL)
                  .where(FEED_URL.ID.eq("feedmiginfy00000000000000000001"))
                  .fetchOne(FEED_URL.FULL_TEXT_METHOD))
          .isEqualTo("playwright_autopagerize");
      assertThat(
              dsl.select(FEED_URL.FULL_TEXT_METHOD)
                  .from(FEED_URL)
                  .where(FEED_URL.ID.eq("feedmiginfy00000000000000000002"))
                  .fetchOne(FEED_URL.FULL_TEXT_METHOD))
          .isEqualTo("playwright_autopagerize_readability");
    } finally {
      dsl.execute(
          """
          ALTER TABLE feed_url
              ADD CONSTRAINT feed_url_full_text_method_check
              CHECK (
                  full_text_method IN (
                      'feed',
                      'http',
                      'http_readability',
                      'http_autopagerize',
                      'http_autopagerize_readability',
                      'playwright',
                      'playwright_readability',
                      'playwright_autopagerize',
                      'playwright_autopagerize_readability'
                  )
              )
          """);
    }
  }

  @Test
  void listNewestFirstOrdersByImportedAtAndId() {
    long older =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "older.json",
                null,
                SHA_A,
                1,
                0,
                0,
                0));
    long newer =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "newer.json",
                null,
                SHA_B,
                1,
                0,
                0,
                0));
    // Distinct sha so third insert is allowed.
    long newest =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "newest.json",
                null,
                SHA_C,
                1,
                0,
                0,
                0));

    List<AutoPagerizeDatasetSummary> list = datasetRepository.listNewestFirst();
    assertThat(list)
        .extracting(AutoPagerizeDatasetSummary::id)
        .containsExactly(newest, newer, older);
  }

  @Test
  void emptyBatchInsertsAreNoOps() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL, null, null, SHA_A, 1, 0, 0, 0));
    ruleRepository.insertRules(List.of());
    ruleRepository.insertRejections(List.of());
    ruleRepository.insertRules(null);
    ruleRepository.insertRejections(null);
    assertThat(ruleRepository.countByDatasetId(datasetId).total()).isZero();
  }

  private static AutoPagerizeRule rule(long datasetId, int ordinal, int matchOrder, String name) {
    return new AutoPagerizeRule(
        datasetId,
        ordinal,
        matchOrder,
        null,
        null,
        name,
        null,
        null,
        null,
        "^https://example\\.com/",
        "//a[@rel='next']",
        "//div[@id='main']",
        null,
        null,
        "{\"name\":\"" + name + "\"}");
  }

  private void insertContentReferencing(long datasetId, int ruleOrdinal) {
    insertFeedAndHeader("headapref00000000000000000000001", "https://example.test/ap-ref");
    dsl.insertInto(CONTENT_FULL_TEXT)
        .set(CONTENT_FULL_TEXT.ID, "textapref00000000000000000000001")
        .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, "headapref00000000000000000000001")
        .set(CONTENT_FULL_TEXT.FULL_TEXT, "body")
        .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_DATASET_ID, datasetId)
        .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_RULE_ORDINAL, ruleOrdinal)
        .execute();
  }

  private void insertFeedAndHeader(String headerId, String url) {
    String feedId = "feedap00000000000000000000000001";
    assertThat(feedId).hasSize(32);
    assertThat(headerId).hasSize(32);
    if (dsl.fetchCount(dsl.selectFrom(FEED_URL).where(FEED_URL.ID.eq(feedId))) == 0) {
      dsl.insertInto(FEED_URL)
          .set(FEED_URL.ID, feedId)
          .set(FEED_URL.URL, "https://example.test/ap-feed.xml")
          .set(FEED_URL.FULL_TEXT_METHOD, "http")
          .execute();
    }
    dsl.insertInto(CONTENT_HEADER)
        .set(CONTENT_HEADER.ID, headerId)
        .set(CONTENT_HEADER.FEED_URL_ID, feedId)
        .set(CONTENT_HEADER.SOURCE_URL, url)
        .set(CONTENT_HEADER.FETCH_URL, url)
        .set(CONTENT_HEADER.CANONICAL_URL, url)
        .set(CONTENT_HEADER.TITLE, "title")
        .execute();
  }
}
