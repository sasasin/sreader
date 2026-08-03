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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.sasasin.sreader.domain.AutoPagerizeDataset;
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
class AutoPagerizeImportServiceIntegrationTest {

  @Autowired AutoPagerizeImportService importService;
  @Autowired AutoPagerizeDatasetRepository datasetRepository;
  @Autowired AutoPagerizeRuleRepository ruleRepository;
  @Autowired AutoPagerizeStateRepository stateRepository;
  @Autowired DSLContext dsl;

  @TempDir Path tempDir;

  @BeforeEach
  void clean() {
    dsl.deleteFrom(CONTENT_FULL_TEXT).execute();
    dsl.deleteFrom(CONTENT_HEADER).execute();
    dsl.deleteFrom(FEED_URL).execute();
    stateRepository.clearActiveDataset();
    dsl.deleteFrom(AUTOPAGERIZE_RULE).execute();
    dsl.deleteFrom(AUTOPAGERIZE_RULE_REJECTION).execute();
    dsl.deleteFrom(AUTOPAGERIZE_DATASET).execute();
  }

  @Test
  void dryRunDoesNotTouchDatabase() throws Exception {
    Path input = writeResource("autopagerize/valid_two_items.json");

    AutoPagerizeImportReport report =
        importService.importFile(input, AutoPagerizeImportOptions.defaults().withDryRun(true));

    assertThat(report.success()).isTrue();
    assertThat(report.dryRun()).isTrue();
    assertThat(report.acceptedCount()).isEqualTo(2);
    assertThat(report.datasetId()).isNull();
    assertThat(dsl.fetchCount(AUTOPAGERIZE_DATASET)).isZero();
    assertThat(stateRepository.findActiveDatasetId()).isEmpty();
  }

  @Test
  void normalImportPersistsDatasetRulesRejectionsAndActivates() throws Exception {
    Path input = writeResource("autopagerize/mixed_valid_and_invalid.json");

    AutoPagerizeImportReport report =
        importService.importFile(
            input, AutoPagerizeImportOptions.defaults().withSourceUri("file:///tmp/mixed.json"));

    assertThat(report.success()).isTrue();
    assertThat(report.activated()).isTrue();
    assertThat(report.reusedExistingDataset()).isFalse();
    assertThat(report.acceptedCount()).isEqualTo(1);
    assertThat(report.rejectedCount()).isEqualTo(2);
    assertThat(report.datasetId()).isNotNull();
    assertThat(report.sourceUri()).isEqualTo("file:///tmp/mixed.json");

    long id = report.datasetId();
    AutoPagerizeDataset dataset = datasetRepository.findById(id).orElseThrow();
    assertThat(dataset.sourceUri()).isEqualTo("file:///tmp/mixed.json");
    assertThat(dataset.acceptedRuleCount()).isEqualTo(1);
    assertThat(dataset.rejectedRuleCount()).isEqualTo(2);
    assertThat(stateRepository.findActiveDatasetId()).contains(id);

    List<AutoPagerizeRule> rules = ruleRepository.findRulesByDatasetIdOrderedByMatchOrder(id);
    assertThat(rules).hasSize(1);
    assertThat(rules.get(0).matchOrder()).isZero();
    assertThat(ruleRepository.findRejectionsByDatasetIdOrderedByOrdinal(id)).hasSize(2);
  }

  @Test
  void noActivateLeavesActivePointerUnchanged() throws Exception {
    Path input = writeResource("autopagerize/valid_two_items.json");

    AutoPagerizeImportReport report =
        importService.importFile(input, AutoPagerizeImportOptions.defaults().withNoActivate(true));

    assertThat(report.success()).isTrue();
    assertThat(report.activated()).isFalse();
    assertThat(report.datasetId()).isNotNull();
    assertThat(stateRepository.findActiveDatasetId()).isEmpty();
    assertThat(datasetRepository.findById(report.datasetId())).isPresent();
  }

  @Test
  void strictModeWithRejectionsDoesNotWrite() throws Exception {
    Path input = writeResource("autopagerize/mixed_valid_and_invalid.json");

    AutoPagerizeImportReport report =
        importService.importFile(input, AutoPagerizeImportOptions.defaults().withStrict(true));

    assertThat(report.success()).isFalse();
    assertThat(report.strict()).isTrue();
    assertThat(report.datasetId()).isNull();
    assertThat(dsl.fetchCount(AUTOPAGERIZE_DATASET)).isZero();
    assertThat(stateRepository.findActiveDatasetId()).isEmpty();
  }

  @Test
  void reimportSameFileIsIdempotentAndCanReactivate() throws Exception {
    Path input = writeResource("autopagerize/valid_two_items.json");

    AutoPagerizeImportReport first =
        importService.importFile(input, AutoPagerizeImportOptions.defaults());
    assertThat(first.reusedExistingDataset()).isFalse();
    long firstId = first.datasetId();

    stateRepository.clearActiveDataset();

    AutoPagerizeImportReport secondNoActivate =
        importService.importFile(input, AutoPagerizeImportOptions.defaults().withNoActivate(true));
    assertThat(secondNoActivate.success()).isTrue();
    assertThat(secondNoActivate.reusedExistingDataset()).isTrue();
    assertThat(secondNoActivate.datasetId()).isEqualTo(firstId);
    assertThat(secondNoActivate.activated()).isFalse();
    assertThat(stateRepository.findActiveDatasetId()).isEmpty();
    assertThat(dsl.fetchCount(AUTOPAGERIZE_DATASET)).isEqualTo(1);

    AutoPagerizeImportReport secondActivate =
        importService.importFile(input, AutoPagerizeImportOptions.defaults());
    assertThat(secondActivate.reusedExistingDataset()).isTrue();
    assertThat(secondActivate.datasetId()).isEqualTo(firstId);
    assertThat(secondActivate.activated()).isTrue();
    assertThat(stateRepository.findActiveDatasetId()).contains(firstId);
    assertThat(dsl.fetchCount(AUTOPAGERIZE_DATASET)).isEqualTo(1);
  }

  @Test
  void activateExistingDatasetIsAtomic() throws Exception {
    Path a = writeNamed("a.json", validSingle("site-a", "^https://a[.]example/"));
    Path b = writeNamed("b.json", validSingle("site-b", "^https://b[.]example/"));

    long idA =
        importService
            .importFile(a, AutoPagerizeImportOptions.defaults().withNoActivate(true))
            .datasetId();
    long idB =
        importService
            .importFile(b, AutoPagerizeImportOptions.defaults().withNoActivate(true))
            .datasetId();

    importService.activateDataset(idA);
    assertThat(stateRepository.findActiveDatasetId()).contains(idA);
    importService.activateDataset(idB);
    assertThat(stateRepository.findActiveDatasetId()).contains(idB);

    assertThatThrownBy(() -> importService.activateDataset(999_999L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void concurrentActivateDoesNotExposeBrokenIntermediateState() throws Exception {
    Path a = writeNamed("c1.json", validSingle("c1", "^https://c1[.]example/"));
    Path b = writeNamed("c2.json", validSingle("c2", "^https://c2[.]example/"));
    long idA =
        importService
            .importFile(a, AutoPagerizeImportOptions.defaults().withNoActivate(true))
            .datasetId();
    long idB =
        importService
            .importFile(b, AutoPagerizeImportOptions.defaults().withNoActivate(true))
            .datasetId();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Long> f1 =
          pool.submit(
              () -> {
                start.await();
                return importService.activateDataset(idA);
              });
      Future<Long> f2 =
          pool.submit(
              () -> {
                start.await();
                return importService.activateDataset(idB);
              });
      start.countDown();
      f1.get(30, TimeUnit.SECONDS);
      f2.get(30, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    Optional<Long> active = stateRepository.findActiveDatasetId();
    assertThat(active).isPresent();
    assertThat(active.get()).isIn(idA, idB);
    // State row remains a single valid pointer (never null mid-switch for activate path).
    assertThat(stateRepository.findActiveState().activeDatasetId()).isIn(idA, idB);
  }

  @Test
  void duplicateDiagnosticCountsExactCoreTriple() {
    byte[] json =
        """
        [
          {
            "name": "one",
            "data": {
              "url": "^https://dup[.]example/",
              "nextLink": "//a",
              "pageElement": "//div"
            }
          },
          {
            "name": "two",
            "data": {
              "url": "^https://dup[.]example/",
              "nextLink": "//a",
              "pageElement": "//div"
            }
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);
    AutoPagerizeImportReport report =
        importService.importBytes(
            json, "dup.json", AutoPagerizeImportOptions.defaults().withDryRun(true));
    assertThat(report.success()).isTrue();
    assertThat(report.acceptedCount()).isEqualTo(2);
    assertThat(report.duplicateDiagnosticCount()).isEqualTo(1);
  }

  @Test
  void zeroAcceptedRulesFailsEntireImport() {
    byte[] onlyInvalid =
        """
        [{"name":"x","data":{"url":"[","nextLink":"//a","pageElement":"//div"}}]
        """
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
            () ->
                importService.importBytes(
                    onlyInvalid, "bad.json", AutoPagerizeImportOptions.defaults()))
        .isInstanceOf(AutoPagerizeImportException.class)
        .hasMessageContaining("No accepted rules");
    assertThat(dsl.fetchCount(AUTOPAGERIZE_DATASET)).isZero();
  }

  @Test
  void strictReimportWithRejectionsAbortsBeforeDbChange() throws Exception {
    Path input = writeResource("autopagerize/mixed_valid_and_invalid.json");
    AutoPagerizeImportReport first =
        importService.importFile(input, AutoPagerizeImportOptions.defaults().withNoActivate(true));
    assertThat(first.success()).isTrue();
    assertThat(first.rejectedCount()).isGreaterThan(0);
    long id = first.datasetId();

    // strict + any input rejection aborts without touching DB (including active pointer).
    AutoPagerizeImportReport second =
        importService.importFile(input, AutoPagerizeImportOptions.defaults().withStrict(true));
    assertThat(second.success()).isFalse();
    assertThat(second.datasetId()).isNull();
    assertThat(second.activated()).isFalse();
    assertThat(stateRepository.findActiveDatasetId()).isEmpty();
    assertThat(dsl.fetchCount(AUTOPAGERIZE_DATASET)).isEqualTo(1);
    assertThat(datasetRepository.findById(id)).isPresent();
  }

  @Test
  void missingInputFileFails() {
    assertThatThrownBy(
            () ->
                importService.importFile(
                    tempDir.resolve("missing.json"), AutoPagerizeImportOptions.defaults()))
        .isInstanceOf(AutoPagerizeImportException.class)
        .hasMessageContaining("regular file");
  }

  @Test
  void sourceUriBlankIsStoredAsNull() throws Exception {
    Path input = writeResource("autopagerize/valid_two_items.json");
    AutoPagerizeImportReport report =
        importService.importFile(input, AutoPagerizeImportOptions.defaults().withSourceUri("   "));
    assertThat(report.success()).isTrue();
    assertThat(datasetRepository.findById(report.datasetId()).orElseThrow().sourceUri()).isNull();
  }

  @Test
  void fileSizeLimitIsEnforced() {
    AutoPagerizeImportService tiny =
        new AutoPagerizeImportService(
            new AutoPagerizeJsonParser(
                new AutoPagerizeUrlPatternCompiler(), new AutoPagerizeXPathSyntaxChecker()),
            new AutoPagerizeImportPersister(datasetRepository, ruleRepository, stateRepository),
            datasetRepository,
            stateRepository,
            16);
    byte[] bytes = "[]".getBytes(StandardCharsets.UTF_8);
    // still small; create oversized
    byte[] large = new byte[32];
    assertThatThrownBy(
            () -> tiny.importBytes(large, "big.json", AutoPagerizeImportOptions.defaults()))
        .isInstanceOf(AutoPagerizeImportException.class)
        .hasMessageContaining("max size");
    assertThat(bytes.length).isLessThan(32);
  }

  private Path writeResource(String classpath) throws Exception {
    byte[] bytes = getClass().getClassLoader().getResourceAsStream(classpath).readAllBytes();
    Path path = tempDir.resolve(Path.of(classpath).getFileName().toString());
    Files.write(path, bytes);
    return path;
  }

  private Path writeNamed(String name, String json) throws Exception {
    Path path = tempDir.resolve(name);
    Files.writeString(path, json, StandardCharsets.UTF_8);
    return path;
  }

  private static String validSingle(String name, String urlPattern) {
    return """
    [
      {
        "name": "%s",
        "data": {
          "url": "%s",
          "nextLink": "//a[@rel='next']",
          "pageElement": "//div[@class='body']"
        }
      }
    ]
    """
        .formatted(name, urlPattern);
  }
}
