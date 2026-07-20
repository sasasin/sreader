package net.sasasin.sreader.service.canonicalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.ContentCanonicalizationCandidate;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationHeader;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;
import net.sasasin.sreader.service.article.ArticleUrlCanonicalizerFixtures;
import org.junit.jupiter.api.Test;

class ContentCanonicalizationCandidateScannerTest {

  private final ContentCanonicalizationMaintenanceRepository repository = mock();
  private final ContentCanonicalizationCandidateScanner scanner =
      new ContentCanonicalizationCandidateScanner(
          ArticleUrlCanonicalizerFixtures.configuredFor("example.test", "/n/"), repository);

  @Test
  void advancesByRawCandidateAndEmitsNormalizedGroupOnlyOnce() {
    String canonical = "https://example.test/n/article";
    when(repository.findCandidateCanonicalUrls("example.test", null, 2))
        .thenReturn(List.of(canonical + "?gs=one", canonical + "?gs=two"));
    when(repository.findCandidateCanonicalUrls("example.test", canonical + "?gs=two", 2))
        .thenReturn(List.of());
    when(repository.loadGroup(canonical))
        .thenReturn(new ContentCanonicalizationGroup(canonical, List.of(member(canonical)), 3));

    ContentCanonicalizationCandidateScanner.Session session = scanner.start("example.test", 2);
    ContentCanonicalizationCandidateScanner.Page first = session.next();
    ContentCanonicalizationCandidateScanner.Page second = session.next();

    assertThat(first.scannedRows()).isEqualTo(2);
    assertThat(first.groups())
        .singleElement()
        .extracting(ContentCanonicalizationCandidateScanner.GroupCandidate::normalizedUrl)
        .isEqualTo(canonical);
    assertThat(second.isFinished()).isTrue();
    verify(repository).loadGroup(canonical);
    verify(repository).findCandidateCanonicalUrls("example.test", canonical + "?gs=two", 2);
  }

  private ContentCanonicalizationCandidate member(String canonical) {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    return new ContentCanonicalizationCandidate(
        new ContentCanonicalizationHeader(
            "id", "feed", canonical, canonical, canonical, "title", now, "text", now, now),
        Optional.empty());
  }
}
