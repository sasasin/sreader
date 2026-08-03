package net.sasasin.sreader.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AutoPagerizeDomainTest {

  private static final String SHA =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  void datasetCreateDefaultsBlankMetadataAndValidatesCounts() {
    AutoPagerizeDatasetCreate create =
        new AutoPagerizeDatasetCreate(
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL, "f.json", null, SHA, 1, 2, 1, 1);
    assertThat(create.metadataJson()).isEqualTo("{}");

    AutoPagerizeDatasetCreate withBlankMetadata =
        new AutoPagerizeDatasetCreate(
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            "f.json",
            null,
            SHA,
            1,
            0,
            0,
            0,
            "  ");
    assertThat(withBlankMetadata.metadataJson()).isEqualTo("{}");

    AutoPagerizeDatasetCreate withNullMetadata =
        new AutoPagerizeDatasetCreate(
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            "f.json",
            null,
            SHA,
            1,
            0,
            0,
            0,
            null);
    assertThat(withNullMetadata.metadataJson()).isEqualTo("{}");

    AutoPagerizeDatasetCreate withMetadata =
        new AutoPagerizeDatasetCreate(
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            "f.json",
            null,
            SHA,
            1,
            0,
            0,
            0,
            "{\"k\":1}");
    assertThat(withMetadata.metadataJson()).isEqualTo("{\"k\":1}");

    assertThatThrownBy(
            () ->
                new AutoPagerizeDatasetCreate(
                    AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL, null, null, SHA, 1, 1, 1, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inputItemCount");

    assertThatThrownBy(
            () ->
                new AutoPagerizeDatasetCreate(
                    AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                    null,
                    null,
                    SHA,
                    1,
                    -1,
                    0,
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("counts");

    assertThatThrownBy(
            () ->
                new AutoPagerizeDatasetCreate(
                    AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                    null,
                    null,
                    SHA,
                    1,
                    0,
                    -1,
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("counts");

    assertThatThrownBy(
            () ->
                new AutoPagerizeDatasetCreate(
                    AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                    null,
                    null,
                    SHA,
                    1,
                    0,
                    0,
                    -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("counts");

    assertThatThrownBy(() -> new AutoPagerizeDatasetCreate(" ", null, null, SHA, 1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("format");

    assertThatThrownBy(
            () ->
                new AutoPagerizeDatasetCreate(
                    AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
                    null,
                    null,
                    "NOT-HEX",
                    1,
                    0,
                    0,
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sourceSha256");

    assertThatThrownBy(
            () ->
                new AutoPagerizeDatasetCreate(
                    AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL, null, null, SHA, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("importerVersion");
  }

  @Test
  void datasetAndSummaryRejectInvalidState() {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    AutoPagerizeDataset dataset =
        new AutoPagerizeDataset(
            1L,
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            null,
            null,
            SHA,
            1,
            now,
            0,
            0,
            0,
            "{}");
    assertThat(dataset.id()).isEqualTo(1L);

    assertThatThrownBy(
            () -> new AutoPagerizeDataset(1L, " ", null, null, SHA, 1, now, 0, 0, 0, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("format");

    assertThatThrownBy(
            () -> new AutoPagerizeDataset(1L, "f", null, null, "ABC", 1, now, 0, 0, 0, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sourceSha256");

    assertThatThrownBy(
            () -> new AutoPagerizeDataset(1L, "f", null, null, SHA, 0, now, 0, 0, 0, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("importerVersion");

    assertThatThrownBy(
            () -> new AutoPagerizeDataset(1L, "f", null, null, SHA, 1, now, -1, 0, 0, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("counts");

    assertThatThrownBy(
            () -> new AutoPagerizeDataset(1L, "f", null, null, SHA, 1, now, 0, -1, 0, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("counts");

    assertThatThrownBy(
            () -> new AutoPagerizeDataset(1L, "f", null, null, SHA, 1, now, 0, 0, -1, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("counts");

    assertThatThrownBy(
            () -> new AutoPagerizeDataset(1L, "f", null, null, SHA, 1, now, 1, 1, 1, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inputItemCount");

    AutoPagerizeDatasetSummary summary =
        new AutoPagerizeDatasetSummary(
            1L,
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            "f.json",
            null,
            SHA,
            1,
            now,
            0,
            0,
            0);
    assertThat(summary.sourceFilename()).isEqualTo("f.json");
  }

  @Test
  void ruleAndRejectionValidateRequiredFields() {
    AutoPagerizeRule rule =
        new AutoPagerizeRule(
            1L,
            0,
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
            "{}");
    assertThat(rule.matchOrder()).isZero();

    assertThatThrownBy(
            () ->
                new AutoPagerizeRule(
                    1L, -1, 0, null, null, null, null, null, null, "u", "n", "p", null, null, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ordinal");

    assertThatThrownBy(
            () ->
                new AutoPagerizeRule(
                    1L, 0, -1, null, null, null, null, null, null, "u", "n", "p", null, null, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("matchOrder");

    assertThatThrownBy(
            () ->
                new AutoPagerizeRule(
                    1L, 0, 0, null, null, null, null, null, null, " ", "n", "p", null, null, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("urlPattern");

    assertThatThrownBy(
            () ->
                new AutoPagerizeRule(
                    1L, 0, 0, null, null, null, null, null, null, "u", " ", "p", null, null, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nextLinkXpath");

    assertThatThrownBy(
            () ->
                new AutoPagerizeRule(
                    1L, 0, 0, null, null, null, null, null, null, "u", "n", " ", null, null, "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pageElementXpath");

    assertThatThrownBy(
            () ->
                new AutoPagerizeRule(
                    1L, 0, 0, null, null, null, null, null, null, "u", "n", "p", null, null, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawItemJson");

    AutoPagerizeRuleRejection rejection =
        new AutoPagerizeRuleRejection(1L, 0, "x", "{}", "[{\"code\":\"e\"}]");
    assertThat(rejection.errorsJson()).contains("code");

    assertThatThrownBy(() -> new AutoPagerizeRuleRejection(1L, -1, null, "{}", "[]"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AutoPagerizeRuleRejection(1L, 0, null, " ", "[]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawItemJson");

    assertThatThrownBy(() -> new AutoPagerizeRuleRejection(1L, 0, null, "{}", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("errorsJson");

    assertThatThrownBy(() -> new AutoPagerizeRuleRejection(1L, 0, null, "{}", "\"not-structured\""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON array or object");
  }

  @Test
  void ruleCountsTotalAndRejectNegative() {
    AutoPagerizeRuleCounts counts = new AutoPagerizeRuleCounts(2, 3);
    assertThat(counts.total()).isEqualTo(5);
    assertThatThrownBy(() -> new AutoPagerizeRuleCounts(-1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AutoPagerizeRuleCounts(0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void activeStateHoldsNullableFields() {
    AutoPagerizeActiveState empty = new AutoPagerizeActiveState(null, null);
    assertThat(empty.activeDatasetId()).isNull();
    AutoPagerizeActiveState active =
        new AutoPagerizeActiveState(42L, OffsetDateTime.parse("2026-02-01T00:00:00Z"));
    assertThat(active.activeDatasetId()).isEqualTo(42L);
  }

  @Test
  void formatConstantIsWedataItemsAll() {
    assertThat(AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL)
        .isEqualTo("wedata-autopagerize-items-all");
  }
}
