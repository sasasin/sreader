package net.sasasin.sreader.service.autopagerize;

import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_DATASET;
import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_RULE;
import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_RULE_REJECTION;
import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.FEED_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.sasasin.sreader.domain.AutoPagerizeDatasetCreate;
import net.sasasin.sreader.domain.AutoPagerizeFormats;
import net.sasasin.sreader.domain.AutoPagerizeRule;
import net.sasasin.sreader.repository.AutoPagerizeDatasetRepository;
import net.sasasin.sreader.repository.AutoPagerizeRuleRepository;
import net.sasasin.sreader.repository.AutoPagerizeStateRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AutoPagerizeRuleCatalogIntegrationTest {

  @Autowired AutoPagerizeRuleCatalog catalog;
  @Autowired AutoPagerizeImportService importService;
  @Autowired AutoPagerizeDatasetRepository datasetRepository;
  @Autowired AutoPagerizeRuleRepository ruleRepository;
  @Autowired AutoPagerizeStateRepository stateRepository;
  @Autowired DSLContext dsl;

  @TempDir Path tempDir;

  @BeforeEach
  void clean() {
    catalog.clearCache();
    dsl.deleteFrom(CONTENT_FULL_TEXT).execute();
    dsl.deleteFrom(CONTENT_HEADER).execute();
    dsl.deleteFrom(FEED_URL).execute();
    stateRepository.clearActiveDataset();
    dsl.deleteFrom(AUTOPAGERIZE_RULE).execute();
    dsl.deleteFrom(AUTOPAGERIZE_RULE_REJECTION).execute();
    dsl.deleteFrom(AUTOPAGERIZE_DATASET).execute();
  }

  @Test
  void activeNullReturnsEmpty() {
    assertThat(catalog.getActiveSnapshot()).isEmpty();
  }

  @Test
  void loadsActiveSnapshotInMatchOrderAndReusesCache() throws Exception {
    Path input = writeValidTwoItems();
    AutoPagerizeImportReport report =
        importService.importFile(input, AutoPagerizeImportOptions.defaults());
    long datasetId = report.datasetId();

    AutoPagerizeRuleSnapshot first = catalog.getActiveSnapshot().orElseThrow();
    AutoPagerizeRuleSnapshot second = catalog.getActiveSnapshot().orElseThrow();

    assertThat(first.datasetId()).isEqualTo(datasetId);
    assertThat(first).isSameAs(second);
    assertThat(first.rules()).hasSize(2);
    assertThat(first.rules().get(0).matchOrder()).isZero();
    // Longer url pattern first.
    assertThat(first.rules().get(0).urlPatternSource().length())
        .isGreaterThanOrEqualTo(first.rules().get(1).urlPatternSource().length());
    assertThat(
            first
                .rules()
                .get(0)
                .urlPattern()
                .matcher("https://longer.example.com/articles/1")
                .find())
        .isTrue();
  }

  @Test
  void activeSwitchReplacesSnapshotAtomicallyWhileOldSnapshotRemainsUsable() throws Exception {
    Path a = writeNamed("a.json", single("A", "^https://a[.]example/"));
    Path b = writeNamed("b.json", single("B", "^https://b[.]example/long/path/"));
    long idA = importService.importFile(a, AutoPagerizeImportOptions.defaults()).datasetId();
    AutoPagerizeRuleSnapshot old = catalog.getActiveSnapshot().orElseThrow();
    assertThat(old.datasetId()).isEqualTo(idA);

    long idB = importService.importFile(b, AutoPagerizeImportOptions.defaults()).datasetId();
    AutoPagerizeRuleSnapshot neu = catalog.getActiveSnapshot().orElseThrow();

    assertThat(neu.datasetId()).isEqualTo(idB);
    assertThat(neu).isNotSameAs(old);
    assertThat(old.datasetId()).isEqualTo(idA);
    assertThat(old.rules()).isNotEmpty();
  }

  @Test
  void snapshotByDatasetId() throws Exception {
    Path input = writeValidTwoItems();
    long id =
        importService
            .importFile(input, AutoPagerizeImportOptions.defaults().withNoActivate(true))
            .datasetId();

    assertThat(catalog.getActiveSnapshot()).isEmpty();
    AutoPagerizeRuleSnapshot snapshot = catalog.getSnapshot(id);
    assertThat(snapshot.datasetId()).isEqualTo(id);
    assertThat(snapshot.rules()).hasSize(2);
  }

  @Test
  void corruptedStoredRegexIsInternalConsistencyError() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "broken.json",
                null,
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                1,
                1,
                1,
                0));
    ruleRepository.insertRules(
        List.of(
            new AutoPagerizeRule(
                datasetId,
                0,
                0,
                null,
                null,
                "broken",
                null,
                null,
                null,
                "[unterminated",
                "//a",
                "//div",
                null,
                null,
                "{\"name\":\"broken\"}")));
    stateRepository.activateDataset(datasetId);

    assertThatThrownBy(() -> catalog.getActiveSnapshot())
        .isInstanceOf(AutoPagerizeCatalogException.class)
        .hasMessageContaining("Internal consistency error");
  }

  @Test
  void unknownDatasetFailsClearly() {
    assertThatThrownBy(() -> catalog.getSnapshot(42_424_242L))
        .isInstanceOf(AutoPagerizeCatalogException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void ruleCountMismatchIsCatalogError() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "mismatch.json",
                null,
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                1,
                1,
                1,
                0));
    // accepted_rule_count=1 but no rules inserted
    stateRepository.activateDataset(datasetId);
    assertThatThrownBy(() -> catalog.getActiveSnapshot())
        .isInstanceOf(AutoPagerizeCatalogException.class)
        .hasMessageContaining("rule count mismatch");
  }

  @Test
  void rejectionCountMismatchIsCatalogError() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "rejection-mismatch.json",
                null,
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                1,
                2,
                1,
                1));
    ruleRepository.insertRules(
        List.of(
            new AutoPagerizeRule(
                datasetId,
                0,
                0,
                null,
                null,
                "valid",
                null,
                null,
                null,
                "^https://valid[.]example/",
                "//a",
                "//div",
                null,
                null,
                "{\"name\":\"valid\"}")));
    stateRepository.activateDataset(datasetId);

    assertThatThrownBy(() -> catalog.getActiveSnapshot())
        .isInstanceOf(AutoPagerizeCatalogException.class)
        .hasMessageContaining("rejection count mismatch");
  }

  @Test
  void matchOrderGapIsCatalogError() {
    long datasetId =
        datasetRepository.insert(
            new AutoPagerizeDatasetCreate(
                AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                "order-mismatch.json",
                null,
                "abababababababababababababababababababababababababababababababab",
                1,
                1,
                1,
                0));
    ruleRepository.insertRules(
        List.of(
            new AutoPagerizeRule(
                datasetId,
                0,
                1,
                null,
                null,
                "invalid-order",
                null,
                null,
                null,
                "^https://order[.]example/",
                "//a",
                "//div",
                null,
                null,
                "{\"name\":\"invalid-order\"}")));
    stateRepository.activateDataset(datasetId);

    assertThatThrownBy(() -> catalog.getActiveSnapshot())
        .isInstanceOf(AutoPagerizeCatalogException.class)
        .hasMessageContaining("match_order mismatch");
  }

  @Test
  void getSnapshotByIdDoesNotRequireActive() throws Exception {
    Path input = writeValidTwoItems();
    long id =
        importService
            .importFile(input, AutoPagerizeImportOptions.defaults().withNoActivate(true))
            .datasetId();
    AutoPagerizeRuleSnapshot first = catalog.getSnapshot(id);
    AutoPagerizeRuleSnapshot second = catalog.getSnapshot(id);
    assertThat(first.datasetId()).isEqualTo(id);
    // Warm path / cache reuse for same dataset id when cache empty then set.
    assertThat(second.rules()).hasSize(first.rules().size());
  }

  private Path writeValidTwoItems() throws Exception {
    byte[] bytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("autopagerize/valid_two_items.json")
            .readAllBytes();
    Path path = tempDir.resolve("valid_two_items.json");
    Files.write(path, bytes);
    return path;
  }

  private Path writeNamed(String name, String json) throws Exception {
    Path path = tempDir.resolve(name);
    Files.writeString(path, json, StandardCharsets.UTF_8);
    return path;
  }

  private static String single(String name, String urlPattern) {
    return """
    [{"name":"%s","data":{"url":"%s","nextLink":"//a","pageElement":"//div"}}]
    """
        .formatted(name, urlPattern);
  }
}
