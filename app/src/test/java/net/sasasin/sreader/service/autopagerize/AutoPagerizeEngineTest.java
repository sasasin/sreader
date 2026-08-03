package net.sasasin.sreader.service.autopagerize;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoPagerizeEngineTest {

  private AutoPagerizeEngine engine;
  private MutableClock clock;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    AutoPagerizePageAnalyzer analyzer = new AutoPagerizePageAnalyzer();
    engine = new AutoPagerizeEngine(new AutoPagerizeRuleMatcher(analyzer), analyzer, clock);
  }

  @Test
  void emptySnapshotYieldsNoMatchingRule() {
    URI start = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(start, pageHtml("body-1", null));
    PaginationResult result =
        engine.paginate(start, session, snapshot(), PaginationPolicy.defaults());
    assertSucceeded(result, PaginationStopReason.NO_MATCHING_RULE);
    assertThat(((PaginationResult.Succeeded) result).matchedRule()).isEmpty();
    assertThat(result.pages()).hasSize(1);
  }

  @Test
  void urlMismatchYieldsNoMatchingRule() {
    URI start = URI.create("https://other.example/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(start, pageHtml("body", "https://other.example/a/2"));
    PaginationResult result =
        engine.paginate(start, session, snapshot(defaultRule()), PaginationPolicy.defaults());
    assertSucceeded(result, PaginationStopReason.NO_MATCHING_RULE);
  }

  @Test
  void urlMatchButMissingNextLinkSkipsToNextRule() {
    CompiledAutoPagerizeRule first =
        rule(0, 0, "^https://example\\.com/", "//a[@rel='missing']", "//div[@class='body']");
    CompiledAutoPagerizeRule second = defaultRule(1, 1);
    URI start = URI.create("https://example.com/a/1");
    URI next = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(start, pageHtml("page-1", next.toString()))
            .put(next, pageHtml("page-2", null));
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(start, session, snapshot(first, second), PaginationPolicy.defaults()),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.matchedRule()).contains(second);
    assertThat(succeeded.pages()).hasSize(2);
  }

  @Test
  void urlMatchButMissingPageElementSkipsToNextRule() {
    CompiledAutoPagerizeRule first =
        rule(0, 0, "^https://example\\.com/", "//a[@rel='next']", "//div[@class='missing']");
    CompiledAutoPagerizeRule second = defaultRule(1, 1);
    URI start = URI.create("https://example.com/a/1");
    URI next = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(start, pageHtml("page-1", next.toString()))
            .put(next, pageHtml("page-2", null));
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(start, session, snapshot(first, second), PaginationPolicy.defaults()),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.matchedRule()).contains(second);
  }

  @Test
  void matchOrderReflectsImportOrderLongerPatternsFirst() {
    CompiledAutoPagerizeRule longer =
        rule(0, 0, "^https://example\\.com/articles/", "//a[@rel='next']", "//div[@class='body']");
    CompiledAutoPagerizeRule shorter =
        rule(1, 1, "^https://example\\.com/", "//a[@rel='next']", "//div[@class='body']");
    URI start = URI.create("https://example.com/articles/1");
    URI next = URI.create("https://example.com/articles/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(start, pageHtml("article", next.toString()))
            .put(next, pageHtml("two", null));
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(start, session, snapshot(longer, shorter), PaginationPolicy.defaults()),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.matchedRule()).contains(longer);
    assertThat(succeeded.matchedRule().orElseThrow().matchOrder()).isZero();
  }

  @Test
  void tracksThreePagesSuccessfully() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    URI p3 = URI.create("https://example.com/a/3");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", p3.toString()))
            .put(p3, pageHtml("three", null));
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.pages()).hasSize(3);
    assertThat(succeeded.pages().get(0).pageElementText()).isEqualTo("one");
    assertThat(succeeded.pages().get(1).pageElementText()).isEqualTo("two");
    assertThat(succeeded.pages().get(2).pageElementText()).isEqualTo("three");
  }

  @Test
  void resolvesRelativeNextUrl() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(p1, pageHtml("one", "2")).put(p2, pageHtml("two", null));
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.pages()).hasSize(2);
    assertThat(succeeded.pages().get(0).nextUri()).contains(p2);
  }

  @Test
  void resolvesNextAgainstBaseHref() {
    URI p1 = URI.create("https://example.com/articles/1");
    URI p2 = URI.create("https://cdn.example.com/dir/page2.html");
    String html =
        """
        <html><head><base href="https://cdn.example.com/dir/"></head>
        <body><div class="body">one</div><a rel="next" href="page2.html">n</a></body></html>
        """;
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(p1, html).put(p2, pageHtml("two", null));
    PaginationPolicy policy =
        new PaginationPolicy(20, 5_000_000, 20_000_000, Duration.ofSeconds(120), false);
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(p1, session, snapshot(defaultRule()), policy),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.pages().get(0).nextUri()).contains(p2);
  }

  @Test
  void resolvesNextFromFinalRedirectUri() {
    URI requested = URI.create("https://example.com/start");
    URI finalUri = URI.create("https://example.com/a/1");
    URI next = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(requested, finalUri, pageHtml("one", "2"))
            .put(next, pageHtml("two", null));
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(
                requested, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.pages()).hasSize(2);
    assertThat(succeeded.firstPage().finalUri()).isEqualTo(finalUri);
  }

  @Test
  void prefersHrefThenActionThenValueForNextLink() {
    URI start = URI.create("https://example.com/a/1");
    AutoPagerizePageAnalyzer analyzer = new AutoPagerizePageAnalyzer();
    CompiledAutoPagerizeRule anyNext =
        rule(0, 0, "^https://example\\.com/", "//*[@rel='next']", "//div[@class='body']");
    PageAnalysis href =
        analyzer.analyze(
            PageSnapshot.ofUtf8(
                start,
                start,
                """
                <html><body><div class="body">x</div>
                <a rel="next" href="/a/href" action="/a/action" value="/a/value">n</a>
                </body></html>
                """),
            anyNext);
    assertThat(href.nextUri()).contains(URI.create("https://example.com/a/href"));
    PageAnalysis action =
        analyzer.analyze(
            PageSnapshot.ofUtf8(
                start,
                start,
                """
                <html><body><div class="body">x</div>
                <form rel="next" action="/a/action" value="/a/value"></form></body></html>
                """),
            anyNext);
    assertThat(action.nextUri()).contains(URI.create("https://example.com/a/action"));
    PageAnalysis value =
        analyzer.analyze(
            PageSnapshot.ofUtf8(
                start,
                start,
                """
                <html><body><div class="body">x</div>
                <input rel="next" value="/a/value"/></body></html>
                """),
            anyNext);
    assertThat(value.nextUri()).contains(URI.create("https://example.com/a/value"));
  }

  @Test
  void noNextLinkOnLaterPageIsSuccessfulStop() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", null));
    assertSucceeded(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.NO_NEXT_LINK);
  }

  @Test
  void firstPageWithoutNextLinkIsNoMatchingRule() {
    URI start = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(start, pageHtml("only", null));
    assertSucceeded(
        engine.paginate(start, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.NO_MATCHING_RULE);
  }

  @Test
  void detectsUrlLoop() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", p1.toString()));
    PaginationResult.Failed failed =
        assertFailed(
            engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
            PaginationStopReason.URL_LOOP);
    assertThat(failed.completedPages()).hasSize(2);
  }

  @Test
  void detectsFragmentOnlyLoop() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(p1, pageHtml("one", "https://example.com/a/1#next"));
    assertFailed(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.URL_LOOP);
  }

  @Test
  void detectsContentLoop() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    String sameBody = pageHtml("same-content", null);
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("same-content", p2.toString()))
            .put(p2, sameBody);
    assertFailed(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.CONTENT_LOOP);
  }

  @Test
  void failsOnOffOriginNext() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(p1, pageHtml("one", "https://evil.example/a/2"));
    assertFailed(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.OFF_ORIGIN);
  }

  @Test
  void failsOnRedirectOffOrigin() {
    URI p1 = URI.create("https://example.com/a/1");
    URI nextReq = URI.create("https://example.com/a/2");
    URI redirected = URI.create("https://evil.example/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", nextReq.toString()))
            .put(nextReq, redirected, pageHtml("two", null));
    assertFailed(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.REDIRECT_OFF_ORIGIN);
  }

  @Test
  void detectsRedirectBackToPreviouslyVisitedFinalUri() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, p1, pageHtml("different content", null));

    PaginationResult.Failed failed =
        assertFailed(
            engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
            PaginationStopReason.URL_LOOP);
    assertThat(failed.completedPages()).hasSize(1);
  }

  @Test
  void failsOnUnsupportedScheme() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(p1, pageHtml("one", "javascript:alert(1)"));
    assertFailed(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.UNSUPPORTED_SCHEME);
  }

  @Test
  void failsWhenPageElementMissingOnPageTwo() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(
                p2,
                """
                <html><body><p>no body class</p><a rel="next" href="/a/3">n</a></body></html>
                """);
    PaginationResult.Failed failed =
        assertFailed(
            engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
            PaginationStopReason.PAGE_ELEMENT_MISSING);
    assertThat(failed.completedPages()).hasSize(1);
  }

  @Test
  void failsWhenMaxPagesReachedWithNextStillPresent() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    URI p3 = URI.create("https://example.com/a/3");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", p3.toString()))
            .put(p3, pageHtml("three", "https://example.com/a/4"));
    PaginationPolicy policy =
        new PaginationPolicy(2, 5_000_000, 20_000_000, Duration.ofSeconds(120), true);
    PaginationResult.Failed failed =
        assertFailed(
            engine.paginate(p1, session, snapshot(defaultRule()), policy),
            PaginationStopReason.MAX_PAGES);
    assertThat(failed.completedPages()).hasSize(2);
  }

  @Test
  void failsOnMaxPageBytesAndMaxTotalBytes() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession large =
        new FakeArticlePageSession().putBytes(p1, pageHtml("x", null), 100);
    PaginationPolicy pageLimit = new PaginationPolicy(5, 50, 10_000, Duration.ofSeconds(120), true);
    assertFailed(
        engine.paginate(p1, large, snapshot(defaultRule()), pageLimit),
        PaginationStopReason.MAX_PAGE_BYTES);

    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession total =
        new FakeArticlePageSession()
            .putBytes(p1, pageHtml("one", p2.toString()), 40)
            .putBytes(p2, pageHtml("two", null), 40);
    PaginationPolicy totalLimit = new PaginationPolicy(5, 50, 70, Duration.ofSeconds(120), true);
    assertFailed(
        engine.paginate(p1, total, snapshot(defaultRule()), totalLimit),
        PaginationStopReason.MAX_TOTAL_BYTES);
  }

  @Test
  void failsOnTimeout() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", null));
    PaginationPolicy policy =
        new PaginationPolicy(10, 5_000_000, 20_000_000, Duration.ofSeconds(1), true);
    clock.setInstant(Instant.parse("2026-01-01T00:00:00Z"));
    // advance past deadline after first load by wrapping session
    ArticlePageSession delaying =
        uri -> {
          PageSnapshot snap = session.load(uri);
          clock.advance(Duration.ofSeconds(2));
          return snap;
        };
    assertFailed(
        engine.paginate(p1, delaying, snapshot(defaultRule()), policy),
        PaginationStopReason.TIMEOUT);
  }

  @Test
  void failsWhenFirstPageLoadExceedsTotalTimeoutEvenWithoutMatchingRule() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session = new FakeArticlePageSession().put(p1, pageHtml("one", null));
    ArticlePageSession delaying =
        uri -> {
          PageSnapshot snapshot = session.load(uri);
          clock.advance(Duration.ofSeconds(2));
          return snapshot;
        };
    PaginationPolicy policy =
        new PaginationPolicy(10, 5_000_000, 20_000_000, Duration.ofSeconds(1), true);

    assertFailed(engine.paginate(p1, delaying, snapshot(), policy), PaginationStopReason.TIMEOUT);
  }

  @Test
  void failsWhenLastPageLoadExceedsTotalTimeout() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", null));
    ArticlePageSession delaying =
        uri -> {
          PageSnapshot snapshot = session.load(uri);
          if (uri.equals(p2)) {
            clock.advance(Duration.ofSeconds(2));
          }
          return snapshot;
        };
    PaginationPolicy policy =
        new PaginationPolicy(10, 5_000_000, 20_000_000, Duration.ofSeconds(1), true);

    assertFailed(
        engine.paginate(p1, delaying, snapshot(defaultRule()), policy),
        PaginationStopReason.TIMEOUT);
  }

  @Test
  void failsOnFetchFailure() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .fail(p2, new PageLoadException("boom"));
    assertFailed(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.FETCH_FAILED);
  }

  @Test
  void failsOnInterruptAndPreservesFlag() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session = new FakeArticlePageSession().put(p1, pageHtml("only", null));
    Thread.currentThread().interrupt();
    try {
      assertFailed(
          engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
          PaginationStopReason.INTERRUPTED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      // clear interrupt for subsequent tests
      boolean ignored = Thread.interrupted();
      assertThat(ignored).isTrue();
    }
  }

  @Test
  void failsOnInvalidNextUriWithUserInfo() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().put(p1, pageHtml("one", "https://user:pass@example.com/a/2"));
    assertFailed(
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.INVALID_NEXT_URI);
  }

  @Test
  void failsWhenFirstPageFetchFails() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession().fail(p1, new PageLoadException("down"));
    PaginationResult.Failed failed =
        assertFailed(
            engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
            PaginationStopReason.FETCH_FAILED);
    assertThat(failed.firstPage()).isEmpty();
  }

  @Test
  void failsWhenFirstPageFetchInterrupted() {
    URI p1 = URI.create("https://example.com/a/1");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .fail(p1, new PageLoadException("x", new InterruptedException("stop")));
    try {
      assertFailed(
          engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
          PaginationStopReason.INTERRUPTED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void maxPagesExactlyOnLastPageWithoutNextIsSuccess() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", null));
    PaginationPolicy policy =
        new PaginationPolicy(2, 5_000_000, 20_000_000, Duration.ofSeconds(120), true);
    assertSucceeded(
        engine.paginate(p1, session, snapshot(defaultRule()), policy),
        PaginationStopReason.NO_NEXT_LINK);
  }

  @Test
  void invalidXpathDuringRuleMatchIsSkipped() {
    CompiledAutoPagerizeRule bad =
        rule(0, 0, "^https://example\\.com/", "//[", "//div[@class='body']");
    CompiledAutoPagerizeRule good = defaultRule(1, 1);
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", null));
    PaginationResult.Succeeded succeeded =
        assertSucceeded(
            engine.paginate(p1, session, snapshot(bad, good), PaginationPolicy.defaults()),
            PaginationStopReason.NO_NEXT_LINK);
    assertThat(succeeded.matchedRule()).contains(good);
    assertThat(succeeded.ruleMatchDiagnostics())
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.matchOrder()).isZero();
              assertThat(diagnostic.target())
                  .isEqualTo(AutoPagerizeRuleMatchDiagnostic.Target.NEXT_LINK);
              assertThat(diagnostic.presence())
                  .isEqualTo(AutoPagerizePageAnalyzer.XPathPresence.INVALID);
            });
  }

  @Test
  void ruleMatchDiagnosticsDistinguishEmptySelectionFromInvalidXpath() {
    URI start = URI.create("https://example.com/a/1");
    AutoPagerizePageAnalyzer analyzer = new AutoPagerizePageAnalyzer();
    CompiledAutoPagerizeRule empty =
        rule(0, 0, "^https://example\\.com/", "//a[@rel='missing']", "//div[@class='body']");
    CompiledAutoPagerizeRule invalid =
        rule(1, 1, "^https://example\\.com/", "//[", "//div[@class='body']");
    AutoPagerizeRuleMatchResult result =
        new AutoPagerizeRuleMatcher(analyzer)
            .findMatchingRuleWithDiagnostics(
                PageSnapshot.ofUtf8(start, start, pageHtml("one", start + "/a/2")),
                snapshot(empty, invalid));

    assertThat(result.matchedRule()).isEmpty();
    assertThat(result.diagnostics())
        .extracting(AutoPagerizeRuleMatchDiagnostic::matchOrder)
        .containsExactly(0, 1);
    assertThat(result.diagnostics())
        .extracting(AutoPagerizeRuleMatchDiagnostic::presence)
        .containsExactly(
            AutoPagerizePageAnalyzer.XPathPresence.EMPTY,
            AutoPagerizePageAnalyzer.XPathPresence.INVALID);
  }

  @Test
  void toPaginationPolicyFromProperties() {
    net.sasasin.sreader.config.FeedReaderProperties.Autopagerize cfg =
        new net.sasasin.sreader.config.FeedReaderProperties.Autopagerize(
            3, 1000L, 2000L, Duration.ofSeconds(9), false);
    PaginationPolicy policy = cfg.toPaginationPolicy();
    assertThat(policy.maxPages()).isEqualTo(3);
    assertThat(policy.sameOriginOnly()).isFalse();
  }

  @Test
  void interruptBetweenPagesPreservesFlag() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", null));
    ArticlePageSession interrupting =
        uri -> {
          if (uri.equals(p2)) {
            Thread.currentThread().interrupt();
          }
          return session.load(uri);
        };
    try {
      assertFailed(
          engine.paginate(p1, interrupting, snapshot(defaultRule()), PaginationPolicy.defaults()),
          PaginationStopReason.INTERRUPTED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void pageElementMissingExceptionWithoutMessage() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    AutoPagerizePageAnalyzer analyzer =
        new AutoPagerizePageAnalyzer() {
          @Override
          public PageAnalysis analyze(PageSnapshot snapshot, CompiledAutoPagerizeRule rule) {
            if (snapshot.finalUri().equals(p2)) {
              throw new IllegalStateException();
            }
            return super.analyze(snapshot, rule);
          }
        };
    AutoPagerizeEngine local =
        new AutoPagerizeEngine(new AutoPagerizeRuleMatcher(analyzer), analyzer, clock);
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .put(p2, pageHtml("two", null));
    assertFailed(
        local.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
        PaginationStopReason.PAGE_ELEMENT_MISSING);
  }

  @Test
  void nextPageFetchInterruptedCause() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .fail(p2, new PageLoadException("wrap", new InterruptedException("stop")));
    try {
      assertFailed(
          engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults()),
          PaginationStopReason.INTERRUPTED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void strictFailureDoesNotProduceSucceededResult() {
    URI p1 = URI.create("https://example.com/a/1");
    URI p2 = URI.create("https://example.com/a/2");
    FakeArticlePageSession session =
        new FakeArticlePageSession()
            .put(p1, pageHtml("one", p2.toString()))
            .fail(p2, new PageLoadException("down"));
    PaginationResult result =
        engine.paginate(p1, session, snapshot(defaultRule()), PaginationPolicy.defaults());
    assertThat(result).isInstanceOf(PaginationResult.Failed.class);
    assertThat(((PaginationResult.Failed) result).completedPages()).isNotEmpty();
  }

  private static PaginationResult.Succeeded assertSucceeded(
      PaginationResult result, PaginationStopReason reason) {
    assertThat(result).isInstanceOf(PaginationResult.Succeeded.class);
    PaginationResult.Succeeded succeeded = (PaginationResult.Succeeded) result;
    assertThat(succeeded.stopReason()).isEqualTo(reason);
    return succeeded;
  }

  private static PaginationResult.Failed assertFailed(
      PaginationResult result, PaginationStopReason reason) {
    assertThat(result).isInstanceOf(PaginationResult.Failed.class);
    PaginationResult.Failed failed = (PaginationResult.Failed) result;
    assertThat(failed.stopReason()).isEqualTo(reason);
    return failed;
  }

  private static String pageHtml(String bodyText, String nextHref) {
    String next = nextHref == null ? "" : "<a rel=\"next\" href=\"" + nextHref + "\">next</a>";
    return "<html><body><div class=\"body\">" + bodyText + "</div>" + next + "</body></html>";
  }

  private static CompiledAutoPagerizeRule defaultRule() {
    return defaultRule(0, 0);
  }

  private static CompiledAutoPagerizeRule defaultRule(int ordinal, int matchOrder) {
    return rule(
        ordinal, matchOrder, "^https://example\\.com/", "//a[@rel='next']", "//div[@class='body']");
  }

  private static CompiledAutoPagerizeRule rule(
      int ordinal, int matchOrder, String pattern, String next, String page) {
    return new CompiledAutoPagerizeRule(
        1L,
        ordinal,
        matchOrder,
        "rule-" + ordinal,
        Pattern.compile(pattern),
        pattern,
        next,
        page,
        null,
        null);
  }

  private static AutoPagerizeRuleSnapshot snapshot(CompiledAutoPagerizeRule... rules) {
    return new AutoPagerizeRuleSnapshot(
        1L, "a".repeat(64), AutoPagerizeImporterVersion.CURRENT, List.of(rules));
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;
    private final ZoneOffset zone = ZoneOffset.UTC;

    MutableClock(Instant instant) {
      this.instant = new AtomicReference<>(instant);
    }

    void setInstant(Instant value) {
      instant.set(value);
    }

    void advance(Duration duration) {
      instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneOffset getZone() {
      return zone;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }
}
