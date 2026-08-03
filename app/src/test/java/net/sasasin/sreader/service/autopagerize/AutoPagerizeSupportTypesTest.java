package net.sasasin.sreader.service.autopagerize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.sasasin.sreader.domain.AutoPagerizeFormats;
import org.junit.jupiter.api.Test;

class AutoPagerizeSupportTypesTest {

  @Test
  void issueAndParsedItemValidation() {
    assertThatThrownBy(() -> new AutoPagerizeIssue(" ", "m"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AutoPagerizeIssue("c", " "))
        .isInstanceOf(IllegalArgumentException.class);

    AutoPagerizeParsedItem accepted =
        new AutoPagerizeParsedItem(
            0,
            null,
            null,
            "n",
            null,
            null,
            null,
            "^https://x/",
            "//a",
            "//div",
            null,
            null,
            "{}",
            List.of(),
            List.of(new AutoPagerizeIssue("W", "warn")));
    assertThat(accepted.accepted()).isTrue();
    assertThat(accepted.warnings()).hasSize(1);

    assertThatThrownBy(
            () ->
                new AutoPagerizeParsedItem(
                    -1, null, null, null, null, null, null, null, null, null, null, null, "{}",
                    List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new AutoPagerizeParsedItem(
                    0, null, null, null, null, null, null, null, null, null, null, null, " ",
                    List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void compiledRuleAndSnapshotValidation() {
    Pattern pattern = Pattern.compile("^https://x/");
    CompiledAutoPagerizeRule rule =
        new CompiledAutoPagerizeRule(
            1L, 0, 0, "n", pattern, "^https://x/", "//a", "//div", null, null);
    assertThat(rule.datasetId()).isEqualTo(1L);

    assertThatThrownBy(
            () ->
                new CompiledAutoPagerizeRule(
                    1L, -1, 0, null, pattern, "^https://x/", "//a", "//div", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CompiledAutoPagerizeRule(
                    1L, 0, -1, null, pattern, "^https://x/", "//a", "//div", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CompiledAutoPagerizeRule(
                    1L, 0, 0, null, pattern, " ", "//a", "//div", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CompiledAutoPagerizeRule(
                    1L, 0, 0, null, pattern, "^https://x/", " ", "//div", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CompiledAutoPagerizeRule(
                    1L, 0, 0, null, pattern, "^https://x/", "//a", " ", null, null))
        .isInstanceOf(IllegalArgumentException.class);

    AutoPagerizeRuleSnapshot snapshot =
        new AutoPagerizeRuleSnapshot(
            1L,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            1,
            List.of(rule));
    assertThat(snapshot.size()).isEqualTo(1);

    assertThatThrownBy(() -> new AutoPagerizeRuleSnapshot(1L, "not-hex", 1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new AutoPagerizeRuleSnapshot(
                    1L,
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    0,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void importOptionsHelpersAndReportFields() {
    AutoPagerizeImportOptions options =
        AutoPagerizeImportOptions.defaults()
            .withDryRun(true)
            .withNoActivate(true)
            .withStrict(true)
            .withSourceUri("file:///x");
    assertThat(options.dryRun()).isTrue();
    assertThat(options.noActivate()).isTrue();
    assertThat(options.strict()).isTrue();
    assertThat(options.sourceUri()).isEqualTo("file:///x");

    AutoPagerizeImportReport report =
        new AutoPagerizeImportReport(
            "fmt",
            "f.json",
            "uri",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            1,
            1,
            1,
            0,
            0,
            0,
            true,
            false,
            false,
            true,
            true,
            9L,
            Map.of("X", 1),
            List.of("m"));
    assertThat(report.datasetId()).isEqualTo(9L);
    assertThat(report.rejectionReasonCounts()).containsEntry("X", 1);
  }

  @Test
  void xpathAndUrlCompilersCoverBranches() {
    AutoPagerizeXPathSyntaxChecker xpath = new AutoPagerizeXPathSyntaxChecker();
    assertThat(xpath.isSyntacticallyValid("//a")).isTrue();
    assertThat(xpath.isSyntacticallyValid("///")).isFalse();
    assertThat(xpath.validateSyntax("//a")).isNull();
    assertThat(xpath.validateSyntax("///")).isNotBlank();

    AutoPagerizeUrlPatternCompiler urls = new AutoPagerizeUrlPatternCompiler();
    assertThat(urls.validateSyntax("^https://")).isNull();
    assertThat(urls.validateSyntax("[")).isNotBlank();
    Pattern p = urls.compile("^https://example[.]com/");
    assertThat(urls.sampleMatches(p, "https://example.com/a")).isTrue();
    assertThat(urls.sampleMatches(p, "http://other/")).isFalse();
  }

  @Test
  void importCommandReportWithoutRejectionReasons() {
    // Exercises printReport path where rejection_reasons map is empty and dataset_id is set.
    AutoPagerizeImportReport report =
        new AutoPagerizeImportReport(
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            null,
            null,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            1,
            1,
            1,
            0,
            0,
            0,
            false,
            true,
            false,
            false,
            true,
            5L,
            Map.of(),
            List.of());
    assertThat(report.rejectionReasonCounts()).isEmpty();
    assertThat(report.sourceFilename()).isNull();
  }

  @Test
  void parserCoversNonObjectElementAndNumberId() {
    AutoPagerizeJsonParser parser =
        new AutoPagerizeJsonParser(
            new AutoPagerizeUrlPatternCompiler(), new AutoPagerizeXPathSyntaxChecker());
    List<AutoPagerizeParsedItem> items =
        parser.parseArray(
            """
            [
              "not-object",
              {
                "id": true,
                "name": "bool-id",
                "data": {
                  "url": "^https://x/",
                  "nextLink": "//a",
                  "pageElement": "//div"
                }
              },
              {
                "name": "object-field",
                "resource_url": {"nested": true},
                "data": {
                  "url": "^https://y/",
                  "nextLink": "//a",
                  "pageElement": "//div"
                }
              }
            ]
            """
                .getBytes(StandardCharsets.UTF_8));
    assertThat(items.get(0).accepted()).isFalse();
    assertThat(items.get(0).errors()).extracting(AutoPagerizeIssue::code).contains("INVALID_ITEM");
    assertThat(items.get(1).accepted()).isTrue();
    assertThat(items.get(1).externalId()).isEqualTo("true");
    assertThat(items.get(2).resourceUrl()).isNull();
  }

  @Test
  void importerVersionConstantAndExceptions() {
    assertThat(AutoPagerizeImporterVersion.CURRENT).isEqualTo(1);
    assertThat(new AutoPagerizeImportException("x").getMessage()).isEqualTo("x");
    assertThat(new AutoPagerizeImportException("y", new RuntimeException()).getCause()).isNotNull();
    assertThat(new AutoPagerizeCatalogException("c").getMessage()).isEqualTo("c");
    assertThat(new AutoPagerizeCatalogException("d", new RuntimeException()).getCause())
        .isNotNull();
  }
}
