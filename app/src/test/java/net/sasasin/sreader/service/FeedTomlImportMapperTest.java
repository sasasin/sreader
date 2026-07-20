package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;
import org.junit.jupiter.api.Test;

class FeedTomlImportMapperTest {

  private final FeedTomlImportMapper mapper = new FeedTomlImportMapper();

  @Test
  void acceptsSchemaVersionsOneAndTwoAndRejectsOthers() {
    assertThat(map(1, minimal("https://example.com/a.xml")).issues()).isEmpty();
    assertThat(map(2, minimal("https://example.com/a.xml")).issues()).isEmpty();
    FeedTomlImportMapper.Result unsupported = map(3, minimal("https://example.com/a.xml"));
    assertThat(unsupported.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.equals("unsupported schema_version: 3"));
    FeedTomlImportMapper.Result missingVersion =
        mapper.map(OptionalInt.empty(), List.of(minimal("https://example.com/a.xml")));
    assertThat(missingVersion.feeds()).hasSize(1);
    assertThat(missingVersion.issues()).isEmpty();
  }

  @Test
  void appliesDefaultsNormalizesUrlAndStripsActiveMetadata() {
    FeedTomlEntry entry =
        new FeedTomlEntry(
            0,
            Optional.of("https://example.com/a/../feed.xml"),
            Optional.empty(),
            Optional.of("other"),
            Optional.of("2026-06-14T03:00:00Z"),
            Optional.of("note"),
            Optional.empty(),
            FeedTomlPosition.of(3, 1),
            FeedTomlPosition.of(4, 1));
    FeedTomlImportMapper.Result result = map(2, entry);
    assertThat(result.issues()).isEmpty();
    assertThat(result.feeds())
        .singleElement()
        .extracting(
            FeedTomlService.ImportFeed::url,
            FeedTomlService.ImportFeed::status,
            FeedTomlService.ImportFeed::unsubscribeReason,
            FeedTomlService.ImportFeed::unsubscribedAt,
            FeedTomlService.ImportFeed::note,
            FeedTomlService.ImportFeed::fullTextMethod,
            FeedTomlService.ImportFeed::index)
        .containsExactly(
            "https://example.com/feed.xml",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.HTTP,
            1);
  }

  @Test
  void defaultsUnsubscribedReasonAndParsesTimestamp() {
    FeedTomlEntry entry =
        new FeedTomlEntry(
            0,
            Optional.of("https://example.com/feed.xml"),
            Optional.of("unsubscribed"),
            Optional.empty(),
            Optional.of("2026-06-14T03:00:00-05:00"),
            Optional.of("kept"),
            Optional.of("feed"),
            FeedTomlPosition.of(3, 1),
            FeedTomlPosition.of(4, 1));
    FeedTomlImportMapper.Result result = map(1, entry);
    assertThat(result.feeds())
        .singleElement()
        .extracting(
            FeedTomlService.ImportFeed::unsubscribeReason,
            FeedTomlService.ImportFeed::unsubscribedAt,
            FeedTomlService.ImportFeed::note,
            FeedTomlService.ImportFeed::fullTextMethod)
        .containsExactly(
            UnsubscribeReason.OTHER,
            OffsetDateTime.parse("2026-06-14T03:00:00-05:00"),
            "kept",
            FullTextMethod.FEED);
  }

  @Test
  void rejectsBlankUrlInvalidSchemeUserinfoAndDuplicates() {
    assertThat(map(1, minimal("   ")).issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("url must not be blank"));
    assertThat(map(1, minimal("ftp://example.com/x")).issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("http or https"));
    assertThat(
            map(1, minimal("https://user:pass@example.com/x")).issues().stream()
                .map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("userinfo"));

    FeedTomlImportMapper.Result duplicate =
        mapper.map(
            OptionalInt.of(1),
            List.of(
                entry(0, "https://example.com/a/../feed.xml"),
                entry(1, "https://example.com/feed.xml")));
    assertThat(duplicate.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("duplicates another feed"));
    assertThat(duplicate.feeds()).hasSize(1);
  }

  @Test
  void aggregatesInvalidEnumAndTimestampWithoutThrowing() {
    FeedTomlEntry bad =
        new FeedTomlEntry(
            0,
            Optional.of("https://example.com/feed.xml"),
            Optional.of("paused"),
            Optional.of("closed"),
            Optional.of("2026-06-14"),
            Optional.empty(),
            Optional.of("unknown_method"),
            FeedTomlPosition.of(3, 1),
            FeedTomlPosition.of(4, 1));
    FeedTomlImportMapper.Result result = map(2, bad);
    assertThat(result.feeds()).isEmpty();
    assertThat(result.issues().stream().map(FeedTomlIssue::message))
        .anyMatch(m -> m.contains("status must be active or unsubscribed"))
        .anyMatch(m -> m.contains("unsubscribe_reason is invalid"))
        .anyMatch(m -> m.contains("unsubscribed_at must be an offset date-time"))
        .anyMatch(m -> m.contains("full_text_method is invalid"));
  }

  @Test
  void acceptsAllEnumValuesAndKeepsResultImmutable() {
    for (UnsubscribeReason reason : UnsubscribeReason.values()) {
      for (FullTextMethod method : FullTextMethod.values()) {
        FeedTomlEntry entry =
            new FeedTomlEntry(
                0,
                Optional.of(
                    "https://example.com/" + reason.value() + "-" + method.value() + ".xml"),
                Optional.of("unsubscribed"),
                Optional.of(reason.value()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(method.value()),
                FeedTomlPosition.of(1, 1),
                FeedTomlPosition.of(2, 1));
        FeedTomlImportMapper.Result result = map(2, entry);
        assertThat(result.issues()).isEmpty();
        assertThat(result.feeds().getFirst().unsubscribeReason()).isEqualTo(reason);
        assertThat(result.feeds().getFirst().fullTextMethod()).isEqualTo(method);
      }
    }
    FeedTomlImportMapper.Result result = map(1, minimal("https://example.com/z.xml"));
    assertThatThrownBy(() -> result.feeds().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private FeedTomlImportMapper.Result map(int schemaVersion, FeedTomlEntry... entries) {
    return mapper.map(OptionalInt.of(schemaVersion), List.of(entries));
  }

  private FeedTomlEntry minimal(String url) {
    return entry(0, url);
  }

  private FeedTomlEntry entry(int index, String url) {
    return new FeedTomlEntry(
        index,
        Optional.of(url),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        FeedTomlPosition.of(1, 1),
        FeedTomlPosition.of(2, 1));
  }
}
