package net.sasasin.sreader.service.autopagerize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.Test;

class AutoPagerizeCoreTypesTest {

  @Test
  void pageSnapshotAndPolicyValidation() {
    URI uri = URI.create("https://example.com/");
    assertThatThrownBy(() -> new PageSnapshot(uri, uri, "x", -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(PageSnapshot.ofUtf8(uri, uri, "abc").byteSize()).isEqualTo(3);

    assertThatThrownBy(() -> new PaginationPolicy(0, 1, 1, Duration.ofSeconds(1), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PaginationPolicy(1, 0, 1, Duration.ofSeconds(1), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PaginationPolicy(1, 10, 5, Duration.ofSeconds(1), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PaginationPolicy(1, 1, 1, Duration.ZERO, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PaginationPolicy(1, 1, 1, Duration.ofSeconds(-1), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(PaginationPolicy.defaults().maxPages()).isEqualTo(20);
    assertThat(PaginationStopReason.NO_MATCHING_RULE.isSuccess()).isTrue();
    assertThat(PaginationStopReason.URL_LOOP.isSuccess()).isFalse();
  }

  @Test
  void pageSliceAndResultValidation() {
    URI uri = URI.create("https://example.com/a");
    PageSnapshot snap = PageSnapshot.ofUtf8(uri, uri, "<html/>");
    assertThatThrownBy(() -> PageSlice.withoutPageElement(0, snap))
        .isInstanceOf(IllegalArgumentException.class);
    PageSlice slice = PageSlice.withoutPageElement(1, snap);
    assertThat(slice.pageElementOuterHtml()).isEmpty();

    assertThatThrownBy(
            () ->
                new PaginationResult.Succeeded(
                    snap, Optional.empty(), List.of(slice), PaginationStopReason.URL_LOOP))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PaginationResult.Failed(
                    Optional.of(snap),
                    Optional.empty(),
                    List.of(),
                    PaginationStopReason.NO_NEXT_LINK,
                    OperationFailure.of(
                        FailureStage.FETCH_ARTICLE, FailureKind.IO, uri.toString(), "x")))
        .isInstanceOf(IllegalArgumentException.class);

    PaginationResult.Failed failed =
        new PaginationResult.Failed(
            Optional.empty(),
            Optional.empty(),
            List.of(),
            PaginationStopReason.FETCH_FAILED,
            OperationFailure.of(FailureStage.FETCH_ARTICLE, FailureKind.IO, uri.toString(), "x"));
    assertThat(failed.firstPageOrNull()).isNull();
    assertThat(failed.pages()).isEmpty();

    PaginationResult.Succeeded succeeded =
        new PaginationResult.Succeeded(
            snap, Optional.empty(), List.of(slice), PaginationStopReason.NO_MATCHING_RULE);
    assertThat(succeeded.firstPageOrNull()).isSameAs(snap);
  }

  @Test
  void contentHasherIsStableAndUriSupportEdgeCases() {
    String hash = PageElementContentHasher.sha256Hex("<div>a</div>");
    assertThat(hash).hasSize(64);
    assertThat(PageElementContentHasher.sha256Hex("<div>a</div>")).isEqualTo(hash);

    assertThat(PaginationUriSupport.effectivePort(null, -1)).isEqualTo(-1);
    assertThat(PaginationUriSupport.effectivePort("ftp", -1)).isEqualTo(-1);
    assertThat(PaginationUriSupport.isAllowedScheme(URI.create("HTTP://example.com/"))).isTrue();
    assertThat(PaginationUriSupport.hasUserInfo(URI.create("https://example.com/"))).isFalse();
    assertThat(
            PaginationUriSupport.resolveNextCandidate(
                URI.create("https://example.com/"), org.jsoup.Jsoup.parse("<html></html>"), "   "))
        .isEmpty();
    assertThat(
            PaginationUriSupport.resolveDocumentBase(
                URI.create("https://example.com/a"),
                org.jsoup.Jsoup.parse("<html><head><base href=\"\"></head></html>")))
        .isEqualTo(URI.create("https://example.com/a"));
    assertThat(
            PaginationUriSupport.resolveDocumentBase(
                URI.create("https://example.com/a"),
                org.jsoup.Jsoup.parse("<html><head><base href=\"::bad\"></head></html>")))
        .isEqualTo(URI.create("https://example.com/a"));
  }
}
