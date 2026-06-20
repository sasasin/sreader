package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.junit.jupiter.api.Test;

class FeedTomlServiceTest {

  private final FeedUrlRepository repository = mock(FeedUrlRepository.class);
  private final FeedTomlService service =
      new FeedTomlService(
          repository, Clock.fixed(Instant.parse("2026-06-14T03:00:00Z"), ZoneId.of("Asia/Tokyo")));

  @Test
  void validatesTomlInput() {
    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    [[feeds]]
                    url = "https://example.test/feed.xml"
                    """))
        .isInstanceOf(FeedTomlService.TomlValidationException.class)
        .hasMessageContaining("schema_version is required");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 99
                    [[feeds]]
                    url = "https://example.test/feed.xml"
                    """))
        .hasMessageContaining("unsupported schema_version");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 1
                    [[feeds]]
                    status = "active"
                    """))
        .hasMessageContaining("url is required");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 1
                    [[feeds]]
                    url = "   "
                    """))
        .hasMessageContaining("url must not be blank");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 1
                    [[feeds]]
                    url = "ftp://example.test/feed.xml"
                    """))
        .hasMessageContaining("url scheme must be http or https");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 1
                    [[feeds]]
                    url = "https://user:pass@example.test/feed.xml"
                    """))
        .hasMessageContaining("must not include userinfo");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 1
                    [[feeds]]
                    url = "https://example.test/feed.xml"
                    status = "paused"
                    """))
        .hasMessageContaining("status must be active or unsubscribed");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 1
                    [[feeds]]
                    url = "https://example.test/feed.xml"
                    status = "unsubscribed"
                    unsubscribe_reason = "closed"
                    """))
        .hasMessageContaining("unsubscribe_reason is invalid");

    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 1
                    [[feeds]]
                    url = "https://example.test/a/../feed.xml"
                    [[feeds]]
                    url = "https://example.test/feed.xml"
                    """))
        .hasMessageContaining("duplicates another feed");
  }

  @Test
  void defaultsUnsubscribedReasonToOther() {
    List<FeedTomlService.ImportFeed> feeds =
        service.parse(
            """
            schema_version = 1
            [[feeds]]
            url = "https://example.test/feed.xml"
            status = "unsubscribed"
            """);

    assertThat(feeds)
        .singleElement()
        .extracting(FeedTomlService.ImportFeed::unsubscribeReason)
        .isEqualTo("other");
  }

  @Test
  void exportsFeedsDeterministicallyWithTombstonesByDefault() {
    when(repository.findAllForExport(false))
        .thenReturn(
            List.of(
                new FeedUrl(
                    "1",
                    "https://a.example/feed.xml",
                    FeedStatus.ACTIVE.value(),
                    "site_closed",
                    null,
                    "ignored",
                    FullTextMethod.HTTP),
                new FeedUrl(
                    "2",
                    "https://z.example/feed.xml",
                    FeedStatus.UNSUBSCRIBED.value(),
                    "site_closed",
                    OffsetDateTime.parse("2026-06-14T12:00:00+09:00"),
                    "サイト閉鎖",
                    FullTextMethod.HTTP)));

    String toml = service.exportToml(false);

    assertThat(toml).contains("schema_version = 2");
    assertThat(toml).contains("generated_at = \"2026-06-14T12:00+09:00\"");
    assertThat(toml)
        .contains(
            "url = \"https://a.example/feed.xml\"\n"
                + "status = \"active\"\n"
                + "full_text_method = \"http\"\n");
    assertThat(toml).doesNotContain("ignored");
    assertThat(toml).contains("unsubscribe_reason = \"site_closed\"");
    assertThat(toml).contains("note = \"サイト閉鎖\"");
    assertThat(toml).contains("full_text_method = \"http\"");
    verify(repository).findAllForExport(false);
  }

  @Test
  void activeOnlyExportDelegatesToRepositoryFilter() {
    when(repository.findAllForExport(true)).thenReturn(List.of());

    service.exportToml(true);

    verify(repository).findAllForExport(true);
  }

  @Test
  void importInsertsActiveAndUnsubscribedFeeds() {
    when(repository.findByUrl("https://example.test/a.xml")).thenReturn(Optional.empty());
    when(repository.findByUrl("https://example.test/old.xml")).thenReturn(Optional.empty());

    FeedTomlService.ImportResult result =
        service.importToml(
            """
            schema_version = 1
            [[feeds]]
            url = "https://example.test/a.xml"
            [[feeds]]
            url = "https://example.test/old.xml"
            status = "unsubscribed"
            """,
            new FeedTomlService.ImportOptions(false, false));

    assertThat(result.inserted()).isEqualTo(2);
    assertThat(result.unsubscribed()).isEqualTo(1);
  }

  @Test
  void importMergeRulesProtectUnsubscribedFeeds() {
    when(repository.findByUrl("https://example.test/active.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "1",
                    "https://example.test/active.xml",
                    FeedStatus.ACTIVE.value(),
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP)));
    when(repository.findByUrl("https://example.test/stop.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "2",
                    "https://example.test/stop.xml",
                    FeedStatus.ACTIVE.value(),
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP)));
    when(repository.findByUrl("https://example.test/meta.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "3",
                    "https://example.test/meta.xml",
                    FeedStatus.UNSUBSCRIBED.value(),
                    "other",
                    null,
                    null,
                    FullTextMethod.HTTP)));
    when(repository.findByUrl("https://example.test/conflict.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "4",
                    "https://example.test/conflict.xml",
                    FeedStatus.UNSUBSCRIBED.value(),
                    "other",
                    null,
                    null,
                    FullTextMethod.HTTP)));

    FeedTomlService.ImportResult result =
        service.importToml(
            """
            schema_version = 1
            [[feeds]]
            url = "https://example.test/active.xml"
            status = "active"
            [[feeds]]
            url = "https://example.test/stop.xml"
            status = "unsubscribed"
            unsubscribe_reason = "not_interested"
            [[feeds]]
            url = "https://example.test/meta.xml"
            status = "unsubscribed"
            unsubscribe_reason = "site_closed"
            [[feeds]]
            url = "https://example.test/conflict.xml"
            status = "active"
            """,
            new FeedTomlService.ImportOptions(false, false));

    assertThat(result.unchanged()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(2);
    assertThat(result.unsubscribed()).isEqualTo(1);
    assertThat(result.conflicts()).isEqualTo(1);
    verify(repository).unsubscribe("https://example.test/stop.xml", "not_interested", null, null);
    verify(repository)
        .updateUnsubscribedMetadata("https://example.test/meta.xml", "site_closed", null, null);
    verify(repository).updateFullTextMethod("https://example.test/stop.xml", "http");
  }

  @Test
  void resubscribeOptionAllowsExplicitReactivation() {
    when(repository.findByUrl("https://example.test/conflict.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "4",
                    "https://example.test/conflict.xml",
                    FeedStatus.UNSUBSCRIBED.value(),
                    "other",
                    null,
                    null,
                    FullTextMethod.HTTP)));

    FeedTomlService.ImportResult result =
        service.importToml(
            """
            schema_version = 1
            [[feeds]]
            url = "https://example.test/conflict.xml"
            status = "active"
            """,
            new FeedTomlService.ImportOptions(false, true));

    assertThat(result.resubscribed()).isEqualTo(1);
    verify(repository).resubscribe("https://example.test/conflict.xml");
    verify(repository).updateFullTextMethod("https://example.test/conflict.xml", "http");
  }

  @Test
  void dryRunDoesNotModifyRepository() {
    when(repository.findByUrl("https://example.test/new.xml")).thenReturn(Optional.empty());

    FeedTomlService.ImportResult result =
        service.importToml(
            """
            schema_version = 1
            [[feeds]]
            url = "https://example.test/new.xml"
            """,
            new FeedTomlService.ImportOptions(true, false));

    assertThat(result.inserted()).isEqualTo(1);
    verify(repository).findByUrl("https://example.test/new.xml");
    verifyNoMoreInteractions(repository);
  }

  @Test
  void schemaVersion1DefaultsFullTextMethodToHttpAndAcceptsValidIfPresent() {
    when(repository.findByUrl("https://example.test/v1-no-method.xml"))
        .thenReturn(Optional.empty());
    when(repository.findByUrl("https://example.test/v1-with-method.xml"))
        .thenReturn(Optional.empty());

    FeedTomlService.ImportResult r1 =
        service.importToml(
            """
            schema_version = 1
            [[feeds]]
            url = "https://example.test/v1-no-method.xml"
            """,
            new FeedTomlService.ImportOptions(true, false));
    assertThat(r1.inserted()).isEqualTo(1);

    FeedTomlService.ImportResult r2 =
        service.importToml(
            """
            schema_version = 1
            [[feeds]]
            url = "https://example.test/v1-with-method.xml"
            full_text_method = "playwright_readability"
            """,
            new FeedTomlService.ImportOptions(true, false));
    assertThat(r2.inserted()).isEqualTo(1);
  }

  @Test
  void schemaVersion2RequiresOrDefaultsFullTextMethodAndRejectsInvalid() {
    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 2
                    [[feeds]]
                    url = "https://example.test/bad-method.xml"
                    full_text_method = "unknown_method"
                    """))
        .isInstanceOf(FeedTomlService.TomlValidationException.class)
        .hasMessageContaining("full_text_method is invalid");

    when(repository.findByUrl("https://example.test/v2-default.xml")).thenReturn(Optional.empty());
    FeedTomlService.ImportResult r =
        service.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/v2-default.xml"
            """,
            new FeedTomlService.ImportOptions(true, false));
    assertThat(r.inserted()).isEqualTo(1);
  }

  @Test
  void activeToActiveWithDifferentFullTextMethodIsUpdated() {
    when(repository.findByUrl("https://example.test/js.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "id",
                    "https://example.test/js.xml",
                    FeedStatus.ACTIVE.value(),
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP)));

    FeedTomlService.ImportResult result =
        service.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/js.xml"
            status = "active"
            full_text_method = "playwright_readability"
            """,
            new FeedTomlService.ImportOptions(false, false));

    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.unchanged()).isEqualTo(0);
    verify(repository)
        .updateFullTextMethod("https://example.test/js.xml", "playwright_readability");
  }
}
