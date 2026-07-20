package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import net.sasasin.sreader.domain.UnsubscribeReason;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        .isEqualTo(UnsubscribeReason.OTHER);
  }

  @Test
  void exportsFeedsDeterministicallyWithTombstonesByDefault() {
    when(repository.findAllForExport(false))
        .thenReturn(
            List.of(
                new FeedUrl(
                    "1",
                    "https://a.example/feed.xml",
                    FeedStatus.ACTIVE,
                    null,
                    null,
                    null,
                    FullTextMethod.HTTP_READABILITY),
                new FeedUrl(
                    "2",
                    "https://z.example/feed.xml",
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.SITE_CLOSED,
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
                + "full_text_method = \"http_readability\"\n");
    assertThat(toml).doesNotContain("ignored");
    assertThat(toml).contains("unsubscribe_reason = \"site_closed\"");
    assertThat(toml).contains("note = \"サイト閉鎖\"");
    assertThat(toml).contains("full_text_method = \"http_readability\"");
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
                    FeedStatus.ACTIVE,
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
                    FeedStatus.ACTIVE,
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
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.OTHER,
                    null,
                    null,
                    FullTextMethod.HTTP)));
    when(repository.findByUrl("https://example.test/conflict.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "4",
                    "https://example.test/conflict.xml",
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.OTHER,
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
    verify(repository)
        .unsubscribe("https://example.test/stop.xml", UnsubscribeReason.NOT_INTERESTED, null, null);
    verify(repository)
        .updateUnsubscribedMetadata(
            "https://example.test/meta.xml", UnsubscribeReason.SITE_CLOSED, null, null);
    verify(repository).updateFullTextMethod("https://example.test/stop.xml", FullTextMethod.HTTP);
  }

  @Test
  void resubscribeOptionAllowsExplicitReactivation() {
    when(repository.findByUrl("https://example.test/conflict.xml"))
        .thenReturn(
            Optional.of(
                new FeedUrl(
                    "4",
                    "https://example.test/conflict.xml",
                    FeedStatus.UNSUBSCRIBED,
                    UnsubscribeReason.OTHER,
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
    verify(repository)
        .updateFullTextMethod("https://example.test/conflict.xml", FullTextMethod.HTTP);
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
            full_text_method = "http_readability"
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
                    FeedStatus.ACTIVE,
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
            full_text_method = "http_readability"
            """,
            new FeedTomlService.ImportOptions(false, false));

    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.unchanged()).isEqualTo(0);
    verify(repository)
        .updateFullTextMethod("https://example.test/js.xml", FullTextMethod.HTTP_READABILITY);
  }

  @Test
  void parseAggregatesSyntaxAndFieldErrorsInInputOrder() {
    FeedTomlService.TomlValidationException exception =
        (FeedTomlService.TomlValidationException)
            org.junit.jupiter.api.Assertions.assertThrows(
                FeedTomlService.TomlValidationException.class,
                () ->
                    service.parse(
                        """
                        schema_version = "1"
                        not an assignment
                        [[feeds]]
                        url = relative
                        status = "ACTIVE"
                        note = "bad\\x"
                        [[feeds]]
                        """));

    assertThat(exception.errors())
        .containsExactly(
            "line 2: expected key = value",
            "unsupported schema_version: \"1\"",
            "feeds[1].url must be a TOML string",
            "feeds[1].url is required",
            "feeds[1].status must be active or unsubscribed: ACTIVE",
            "feeds[1].note contains unsupported escape: \\x",
            "feeds[2].url is required");
    assertThat(exception).hasMessage(String.join("; ", exception.errors()));
    verifyNoInteractions(repository);
  }

  @Test
  void parseAcceptsCommentsEscapesAndNormalizesActiveMetadataAway() {
    List<FeedTomlService.ImportFeed> feeds =
        service.parse(
            """
            # comment
            schema_version = 2 # trailing comment
            [[feeds]]
            url = "HTTPS://Example.TEST/a/../feed.xml?x=1#inside"
            status = "active"
            unsubscribe_reason = "other"
            unsubscribed_at = "2026-06-14T03:00:00Z"
            note = "quote: \\"; slash: \\\\; lines: \\n\\r\\t # kept" # discarded for active
            full_text_method = "feed"
            """);

    assertThat(feeds)
        .singleElement()
        .extracting(
            FeedTomlService.ImportFeed::url,
            FeedTomlService.ImportFeed::status,
            FeedTomlService.ImportFeed::unsubscribeReason,
            FeedTomlService.ImportFeed::unsubscribedAt,
            FeedTomlService.ImportFeed::note,
            FeedTomlService.ImportFeed::fullTextMethod)
        .containsExactly(
            "HTTPS://Example.TEST/feed.xml?x=1#inside",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.FEED);
  }

  @Test
  void parseAcceptsAllUnsubscribeReasonsAndFullTextMethodsForBothSchemas() {
    for (int schemaVersion : List.of(1, 2)) {
      for (UnsubscribeReason reason : UnsubscribeReason.values()) {
        for (FullTextMethod method : FullTextMethod.values()) {
          List<FeedTomlService.ImportFeed> feeds =
              service.parse(
                  """
                  schema_version = %d
                  [[feeds]]
                  url = "https://example.test/%s-%s.xml"
                  status = "unsubscribed"
                  unsubscribe_reason = "%s"
                  unsubscribed_at = "2026-06-14T03:00:00-05:00"
                  note = "note"
                  full_text_method = "%s"
                  """
                      .formatted(
                          schemaVersion,
                          reason.value(),
                          method.value(),
                          reason.value(),
                          method.value()));
          assertThat(feeds)
              .singleElement()
              .extracting(
                  FeedTomlService.ImportFeed::unsubscribeReason,
                  FeedTomlService.ImportFeed::unsubscribedAt,
                  FeedTomlService.ImportFeed::fullTextMethod)
              .containsExactly(reason, OffsetDateTime.parse("2026-06-14T03:00:00-05:00"), method);
        }
      }
    }
  }

  @Test
  void parseRejectsInvalidDateStringFormsAndDuplicateNormalizedUrls() {
    assertThatThrownBy(
            () ->
                service.parse(
                    """
                    schema_version = 2
                    [[feeds]]
                    url = "https://example.test/a/../feed.xml"
                    status = "unsubscribed"
                    unsubscribed_at = "2026-06-14"
                    full_text_method = bad
                    [[feeds]]
                    url = "https://example.test/feed.xml"
                    status = "unsubscribed"
                    unsubscribe_reason = "bad"
                    note = "trailing\\"
                    """))
        .isInstanceOf(FeedTomlService.TomlValidationException.class)
        .hasMessageContaining("unsubscribed_at must be an offset date-time: 2026-06-14")
        .hasMessageContaining("full_text_method must be a TOML string")
        .hasMessageContaining(
            "duplicates another feed after normalization: https://example.test/feed.xml")
        .hasMessageContaining("unsubscribe_reason is invalid: bad")
        .hasMessageContaining("note ends with an incomplete escape");
  }

  @Test
  void exportHandlesEmptyNullMetadataAndEscapesRoundTripValues() {
    when(repository.findAllForExport(true)).thenReturn(List.of());
    assertThat(service.exportToml(true)).contains("schema_version = 2").doesNotContain("[[feeds]]");

    FeedUrl unsubscribed =
        new FeedUrl(
            "id",
            "https://example.test/feed.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.OTHER,
            null,
            "quote \" slash \\ LF\nCR\rTAB\t#日本語",
            FullTextMethod.HTTP);
    when(repository.findAllForExport(false)).thenReturn(List.of(unsubscribed));
    String exported = service.exportToml(false);

    assertThat(exported)
        .contains("full_text_method = \"http\"")
        .contains("unsubscribe_reason = \"other\"")
        .doesNotContain("unsubscribed_at")
        .contains("note = \"quote \\\" slash \\\\ LF\\nCR\\rTAB\\t#日本語\"");
    assertThat(service.parse(exported))
        .singleElement()
        .extracting(
            FeedTomlService.ImportFeed::url,
            FeedTomlService.ImportFeed::note,
            FeedTomlService.ImportFeed::fullTextMethod)
        .containsExactly(
            "https://example.test/feed.xml",
            "quote \" slash \\ LF\nCR\rTAB\t#日本語",
            FullTextMethod.HTTP);
  }

  @Test
  void importInsertsExpectedFeedAndDryRunNeverWritesUnsubscribedFeed() {
    when(repository.findByUrl("https://example.test/new.xml")).thenReturn(Optional.empty());
    FeedTomlService.ImportResult inserted =
        service.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/new.xml"
            status = "unsubscribed"
            unsubscribe_reason = "moved"
            unsubscribed_at = "2026-06-14T03:00:00Z"
            note = "moved"
            full_text_method = "feed"
            """,
            new FeedTomlService.ImportOptions(false, false));
    ArgumentCaptor<FeedUrl> captured = ArgumentCaptor.forClass(FeedUrl.class);
    verify(repository).insertFromImport(captured.capture());
    assertThat(inserted)
        .extracting(
            FeedTomlService.ImportResult::inserted,
            FeedTomlService.ImportResult::updated,
            FeedTomlService.ImportResult::unchanged,
            FeedTomlService.ImportResult::unsubscribed)
        .containsExactly(1, 0, 0, 1);
    assertThat(captured.getValue())
        .extracting(
            FeedUrl::url, FeedUrl::status, FeedUrl::unsubscribeReason, FeedUrl::fullTextMethod)
        .containsExactly(
            "https://example.test/new.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.MOVED,
            FullTextMethod.FEED);

    FeedUrlRepository dryRepository = mock(FeedUrlRepository.class);
    when(dryRepository.findByUrl("https://example.test/dry.xml")).thenReturn(Optional.empty());
    FeedTomlService dryService = new FeedTomlService(dryRepository, Clock.systemUTC());
    FeedTomlService.ImportResult dryResult =
        dryService.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/dry.xml"
            status = "unsubscribed"
            """,
            new FeedTomlService.ImportOptions(true, false));
    assertThat(dryResult)
        .extracting(
            FeedTomlService.ImportResult::inserted, FeedTomlService.ImportResult::unsubscribed)
        .containsExactly(1, 1);
    verify(dryRepository).findByUrl("https://example.test/dry.xml");
    verifyNoMoreInteractions(dryRepository);
  }

  @Test
  void importUpdatesEachExistingStatusPathAndLeavesDryRunReadOnly() {
    FeedUrl active =
        feed(
            "https://example.test/active.xml",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.HTTP);
    FeedUrl stopped =
        feed(
            "https://example.test/stopped.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.OTHER,
            null,
            "old",
            FullTextMethod.HTTP);
    when(repository.findByUrl(active.url())).thenReturn(Optional.of(active));
    when(repository.findByUrl(stopped.url())).thenReturn(Optional.of(stopped));

    FeedTomlService.ImportResult result =
        service.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/active.xml"
            full_text_method = "http_readability"
            [[feeds]]
            url = "https://example.test/stopped.xml"
            status = "unsubscribed"
            unsubscribe_reason = "other"
            note = "new"
            full_text_method = "feed"
            """,
            new FeedTomlService.ImportOptions(false, false));
    assertThat(result)
        .extracting(FeedTomlService.ImportResult::updated, FeedTomlService.ImportResult::unchanged)
        .containsExactly(2, 0);
    verify(repository).updateFullTextMethod(active.url(), FullTextMethod.HTTP_READABILITY);
    verify(repository)
        .updateUnsubscribedMetadata(stopped.url(), UnsubscribeReason.OTHER, null, "new");
    verify(repository).updateFullTextMethod(stopped.url(), FullTextMethod.FEED);

    FeedUrlRepository dryRepository = mock(FeedUrlRepository.class);
    when(dryRepository.findByUrl(stopped.url())).thenReturn(Optional.of(stopped));
    FeedTomlService dryService = new FeedTomlService(dryRepository, Clock.systemUTC());
    FeedTomlService.ImportResult dry =
        dryService.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/stopped.xml"
            status = "unsubscribed"
            unsubscribe_reason = "site_closed"
            full_text_method = "feed"
            """,
            new FeedTomlService.ImportOptions(true, false));
    assertThat(dry)
        .extracting(FeedTomlService.ImportResult::updated, FeedTomlService.ImportResult::unchanged)
        .containsExactly(1, 0);
    verify(dryRepository).findByUrl(stopped.url());
    verifyNoMoreInteractions(dryRepository);
  }

  @Test
  void importReportsConflictsInTomlOrderAndResubscribesOnlyWhenEnabled() {
    FeedUrl first =
        feed(
            "https://example.test/one.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.OTHER,
            null,
            null,
            FullTextMethod.HTTP);
    FeedUrl second =
        feed(
            "https://example.test/two.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.OTHER,
            null,
            null,
            FullTextMethod.HTTP);
    when(repository.findByUrl(first.url())).thenReturn(Optional.of(first));
    when(repository.findByUrl(second.url())).thenReturn(Optional.of(second));
    FeedTomlService.ImportResult conflict =
        service.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/one.xml"
            [[feeds]]
            url = "https://example.test/two.xml"
            """,
            new FeedTomlService.ImportOptions(true, false));
    assertThat(conflict)
        .extracting(FeedTomlService.ImportResult::conflicts, FeedTomlService.ImportResult::updated)
        .containsExactly(2, 0);
    assertThat(conflict.conflictMessages())
        .containsExactly(
            "feed[1] https://example.test/one.xml is unsubscribed in DB but active in TOML",
            "feed[2] https://example.test/two.xml is unsubscribed in DB but active in TOML");

    FeedUrlRepository resubscribeRepository = mock(FeedUrlRepository.class);
    when(resubscribeRepository.findByUrl(first.url())).thenReturn(Optional.of(first));
    FeedTomlService resubscribeService =
        new FeedTomlService(resubscribeRepository, Clock.systemUTC());
    FeedTomlService.ImportResult resubscribed =
        resubscribeService.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/one.xml"
            full_text_method = "feed"
            """,
            new FeedTomlService.ImportOptions(false, true));
    assertThat(resubscribed)
        .extracting(
            FeedTomlService.ImportResult::updated,
            FeedTomlService.ImportResult::resubscribed,
            FeedTomlService.ImportResult::conflicts)
        .containsExactly(1, 1, 0);
    verify(resubscribeRepository).resubscribe(first.url());
    verify(resubscribeRepository).updateFullTextMethod(first.url(), FullTextMethod.FEED);
  }

  @Test
  void importPropagatesRepositoryFailuresWithoutProcessingLaterFeeds() {
    RuntimeException failure = new RuntimeException("repository failure");
    when(repository.findByUrl("https://example.test/fail.xml")).thenThrow(failure);
    assertThatThrownBy(
            () ->
                service.importToml(
                    """
                    schema_version = 2
                    [[feeds]]
                    url = "https://example.test/fail.xml"
                    [[feeds]]
                    url = "https://example.test/later.xml"
                    """,
                    new FeedTomlService.ImportOptions(false, false)))
        .isSameAs(failure);
    verify(repository).findByUrl("https://example.test/fail.xml");
    verifyNoMoreInteractions(repository);

    FeedUrl active =
        feed(
            "https://example.test/write.xml",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.HTTP);
    FeedUrlRepository writeRepository = mock(FeedUrlRepository.class);
    when(writeRepository.findByUrl(active.url())).thenReturn(Optional.of(active));
    doThrow(failure).when(writeRepository).updateFullTextMethod(active.url(), FullTextMethod.FEED);
    FeedTomlService writeService = new FeedTomlService(writeRepository, Clock.systemUTC());
    assertThatThrownBy(
            () ->
                writeService.importToml(
                    """
                    schema_version = 2
                    [[feeds]]
                    url = "https://example.test/write.xml"
                    full_text_method = "feed"
                    """,
                    new FeedTomlService.ImportOptions(false, false)))
        .isSameAs(failure);
  }

  @Test
  void importRejectsInvalidTomlBeforeAnyRepositoryAccessAndExceptionIsImmutable() {
    assertThatThrownBy(
            () ->
                service.importToml(
                    """
                    schema_version = 2
                    [[feeds]]
                    status = active
                    """,
                    new FeedTomlService.ImportOptions(false, false)))
        .isInstanceOf(FeedTomlService.TomlValidationException.class);
    verifyNoInteractions(repository);

    List<String> errors = new java.util.ArrayList<>(List.of("first"));
    FeedTomlService.TomlValidationException exception =
        new FeedTomlService.TomlValidationException(errors);
    errors.add("second");
    assertThat(exception.errors()).containsExactly("first");
    assertThatThrownBy(() -> exception.errors().add("third"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void importCountsDryRunStatusChangesWithoutWritingAndTreatsMatchingHttpAsUnchanged() {
    FeedUrl httpActive =
        feed(
            "https://example.test/http-active.xml",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.HTTP);
    FeedUrl active =
        feed(
            "https://example.test/dry-stop.xml",
            FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.HTTP);
    FeedUrl unchanged =
        feed(
            "https://example.test/unchanged.xml",
            FeedStatus.UNSUBSCRIBED,
            UnsubscribeReason.OTHER,
            null,
            null,
            FullTextMethod.HTTP);
    when(repository.findByUrl(httpActive.url())).thenReturn(Optional.of(httpActive));
    when(repository.findByUrl(active.url())).thenReturn(Optional.of(active));
    when(repository.findByUrl(unchanged.url())).thenReturn(Optional.of(unchanged));

    FeedTomlService.ImportResult result =
        service.importToml(
            """
            schema_version = 2
            [[feeds]]
            url = "https://example.test/http-active.xml"
            [[feeds]]
            url = "https://example.test/dry-stop.xml"
            status = "unsubscribed"
            [[feeds]]
            url = "https://example.test/unchanged.xml"
            status = "unsubscribed"
            """,
            new FeedTomlService.ImportOptions(true, false));

    assertThat(result)
        .extracting(
            FeedTomlService.ImportResult::updated,
            FeedTomlService.ImportResult::unchanged,
            FeedTomlService.ImportResult::unsubscribed)
        .containsExactly(1, 2, 1);
    verify(repository).findByUrl(httpActive.url());
    verify(repository).findByUrl(active.url());
    verify(repository).findByUrl(unchanged.url());
    verifyNoMoreInteractions(repository);
  }

  private FeedUrl feed(
      String url,
      FeedStatus status,
      UnsubscribeReason reason,
      OffsetDateTime unsubscribedAt,
      String note,
      FullTextMethod method) {
    return new FeedUrl("id-" + url, url, status, reason, unsubscribedAt, note, method);
  }
}
