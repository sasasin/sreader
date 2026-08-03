package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeEngine;
import net.sasasin.sreader.service.autopagerize.AutoPagerizePageAnalyzer;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleCatalog;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleMatcher;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleSnapshot;
import net.sasasin.sreader.service.autopagerize.CompiledAutoPagerizeRule;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.HttpTransport;
import net.sasasin.sreader.service.outcome.FailureStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end HTTP AutoPagerize extraction against a local {@link HttpServer}. Does not use external
 * network or a real AutoPagerize DB.
 */
class HttpAutopagerizeExtractionIntegrationTest {

  private HttpServer server;
  private URI baseUri;
  private final AtomicInteger page1Hits = new AtomicInteger();
  private final AtomicInteger page2Hits = new AtomicInteger();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext(
        "/articles/1", exchange -> servePage(exchange, "one", "/articles/2", page1Hits));
    server.createContext(
        "/articles/2", exchange -> servePage(exchange, "two", "/articles/3", page2Hits));
    server.createContext(
        "/articles/3", exchange -> servePage(exchange, "three", null, new AtomicInteger()));
    server.createContext(
        "/articles/fail-2",
        exchange -> {
          if (exchange.getRequestURI().getPath().endsWith("fail-2")) {
            write(exchange, 500, "fail");
          }
        });
    server.createContext(
        "/articles/chain-fail/1",
        exchange -> servePage(exchange, "ok-1", "/articles/chain-fail/2", new AtomicInteger()));
    server.createContext("/articles/chain-fail/2", exchange -> write(exchange, 500, "page2-down"));
    server.createContext(
        "/no-rule/1",
        exchange -> write(exchange, 200, "<html><body><main>single only</main></body></html>"));
    server.createContext(
        "/off-origin/1",
        exchange -> write(exchange, 200, pageHtml("origin-1", "http://127.0.0.1:1/elsewhere")));
    server.createContext(
        "/max/1", exchange -> servePage(exchange, "m1", "/max/2", new AtomicInteger()));
    server.createContext(
        "/max/2", exchange -> servePage(exchange, "m2", "/max/3", new AtomicInteger()));
    server.createContext(
        "/max/3", exchange -> servePage(exchange, "m3", "/max/4", new AtomicInteger()));
    server.createContext(
        "/redirect-off/1", exchange -> write(exchange, 200, pageHtml("r1", "/redirect-off/leave")));
    server.createContext(
        "/redirect-off/leave",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "http://127.0.0.1:9/foreign");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.start();
    baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void httpAutopagerizeExtractsThreePages() {
    FullTextExtractionService service = service(snapshotMatchingArticles(), properties(20));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(
                header(baseUri.resolve("/articles/1")), FullTextMethod.HTTP_AUTOPAGERIZE);

    assertThat(extracted.text()).contains("one", "two", "three");
    assertThat(extracted.pagination()).isPresent();
    PaginationMetadata meta = extracted.pagination().orElseThrow();
    assertThat(meta.datasetId()).isEqualTo(41L);
    assertThat(meta.ruleOrdinal()).contains(0);
    assertThat(meta.pageCount()).isEqualTo(3);
    assertThat(meta.stopReason()).isEqualTo(PaginationStopReason.NO_NEXT_LINK);
    assertThat(meta.complete()).isTrue();
    assertThat(page1Hits.get()).isEqualTo(1);
  }

  @Test
  void httpAutopagerizeReadabilityFallsBackToPageElement() {
    FullTextExtractionService service = service(snapshotMatchingArticles(), properties(20));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(
                header(baseUri.resolve("/articles/1")),
                FullTextMethod.HTTP_AUTOPAGERIZE_READABILITY);
    // Minimal HTML has no strong Readability article; pageElement fallback still yields text.
    assertThat(extracted.text()).contains("one", "two", "three");
    assertThat(extracted.pagination()).isPresent();
    assertThat(extracted.pagination().orElseThrow().pageCount()).isEqualTo(3);
  }

  @Test
  void noMatchingRuleFallsBackToSinglePageExtraction() {
    FullTextExtractionService service = service(snapshotMatchingArticles(), properties(20));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(
                header(baseUri.resolve("/no-rule/1")), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(extracted.text()).contains("single only");
    PaginationMetadata meta = extracted.pagination().orElseThrow();
    assertThat(meta.ruleOrdinal()).isEmpty();
    assertThat(meta.pageCount()).isEqualTo(1);
    assertThat(meta.stopReason()).isEqualTo(PaginationStopReason.NO_MATCHING_RULE);
    assertThat(meta.complete()).isTrue();
  }

  @Test
  void missingActiveDatasetIsConfigurationFailure() {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.empty());
    FullTextExtractionService service = service(catalog, properties(20));
    TextExtractionOutcome.Failed failed =
        (TextExtractionOutcome.Failed)
            service.extract(
                header(baseUri.resolve("/articles/1")), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.LOAD_AUTOPAGERIZE_DATABASE);
    assertThat(failed.failure().message()).contains("No active AutoPagerize dataset");
  }

  @Test
  void pageTwoFailureDoesNotProducePartialSuccess() {
    FullTextExtractionService service =
        service(snapshotForPrefix("/articles/chain-fail/"), properties(20));
    TextExtractionOutcome outcome =
        service.extract(
            header(baseUri.resolve("/articles/chain-fail/1")), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.Failed.class);
    TextExtractionOutcome.Failed failed = (TextExtractionOutcome.Failed) outcome;
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_ARTICLE_PAGE);
    assertThat(failed.failure().message()).contains("page 2");
  }

  @Test
  void offOriginNextLinkFailsWithoutPartialText() {
    FullTextExtractionService service = service(snapshotForPrefix("/off-origin/"), properties(20));
    TextExtractionOutcome outcome =
        service.extract(header(baseUri.resolve("/off-origin/1")), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.Failed.class);
    assertThat(((TextExtractionOutcome.Failed) outcome).failure().stage())
        .isEqualTo(FailureStage.ANALYZE_PAGINATION);
  }

  @Test
  void maxPagesStopsAsFailure() {
    FeedReaderProperties props = properties(2);
    FullTextExtractionService service = service(snapshotForPrefix("/max/"), props);
    TextExtractionOutcome outcome =
        service.extract(header(baseUri.resolve("/max/1")), FullTextMethod.HTTP_AUTOPAGERIZE);
    assertThat(outcome).isInstanceOf(TextExtractionOutcome.Failed.class);
    TextExtractionOutcome.Failed failed = (TextExtractionOutcome.Failed) outcome;
    assertThat(failed.failure().message()).containsIgnoringCase("max-pages");
  }

  @Test
  void existingHttpMethodsStillFetchSinglePage() {
    FullTextExtractionService service = service(snapshotMatchingArticles(), properties(20));
    TextExtractionOutcome.Extracted extracted =
        (TextExtractionOutcome.Extracted)
            service.extract(header(baseUri.resolve("/articles/1")), FullTextMethod.HTTP);
    assertThat(extracted.text()).contains("one");
    assertThat(extracted.text()).doesNotContain("two");
    assertThat(extracted.pagination()).isEmpty();
    assertThat(page1Hits.get()).isEqualTo(1);
    assertThat(page2Hits.get()).isZero();
  }

  private FullTextExtractionService service(
      AutoPagerizeRuleSnapshot snapshot, FeedReaderProperties properties) {
    AutoPagerizeRuleCatalog catalog = mock(AutoPagerizeRuleCatalog.class);
    when(catalog.getActiveSnapshot()).thenReturn(Optional.of(snapshot));
    return service(catalog, properties);
  }

  private FullTextExtractionService service(
      AutoPagerizeRuleCatalog catalog, FeedReaderProperties properties) {
    HttpTransport transport = new HttpTransport(properties);
    HttpArticlePageSessionFactory sessions = new HttpArticlePageSessionFactory(transport);
    // Use a real client for single-page regression path as well.
    java.net.http.HttpClient client =
        java.net.http.HttpClient.newBuilder()
            .connectTimeout(properties.http().connectTimeout())
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build();
    HttpFetchService http = new HttpFetchService(transport, client);
    ExtractRuleService rules = mock(ExtractRuleService.class);
    when(rules.findBestRule(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    HtmlTextExtractor html = new HtmlTextExtractor(rules, new ReadabilityArticleParser());
    PaginatedHtmlTextExtractor paginated =
        new PaginatedHtmlTextExtractor(html, rules, new ReadabilityArticleParser());
    AutoPagerizePageAnalyzer analyzer = new AutoPagerizePageAnalyzer();
    AutoPagerizeEngine engine =
        new AutoPagerizeEngine(new AutoPagerizeRuleMatcher(analyzer), analyzer, Clock.systemUTC());
    return new FullTextExtractionService(
        mock(ContentHeaderRepository.class),
        mock(ContentFullTextWriter.class),
        html,
        paginated,
        http,
        sessions,
        catalog,
        engine,
        mock(PlaywrightHtmlSource.class),
        properties);
  }

  private static ContentHeader header(URI fetchUrl) {
    return new ContentHeader(
        "id",
        "feed",
        fetchUrl.toString(),
        fetchUrl.toString(),
        fetchUrl.toString(),
        "title",
        null,
        null);
  }

  private static AutoPagerizeRuleSnapshot snapshotMatchingArticles() {
    return snapshotForPrefix("/articles/");
  }

  private static AutoPagerizeRuleSnapshot snapshotForPrefix(String pathPrefix) {
    String pattern = "^http://127\\.0\\.0\\.1:\\d+" + Pattern.quote(pathPrefix);
    CompiledAutoPagerizeRule rule =
        new CompiledAutoPagerizeRule(
            41L,
            0,
            0,
            "local-test",
            Pattern.compile(pattern),
            pattern,
            "//a[@rel='next']",
            "//div[@class='body']",
            null,
            null);
    return new AutoPagerizeRuleSnapshot(41L, "a".repeat(64), 1, List.of(rule));
  }

  private FeedReaderProperties properties(int maxPages) {
    return new FeedReaderProperties(
        null,
        null,
        new FeedReaderProperties.Http(
            "test-agent", Duration.ofSeconds(1), Duration.ofSeconds(3), 0),
        null,
        null,
        new FeedReaderProperties.Autopagerize(
            maxPages, 5L * 1024 * 1024, 20L * 1024 * 1024, Duration.ofSeconds(30), true),
        null);
  }

  private static void servePage(
      HttpExchange exchange, String marker, String nextPath, AtomicInteger hits)
      throws IOException {
    hits.incrementAndGet();
    write(exchange, 200, pageHtml(marker, nextPath));
  }

  private static String pageHtml(String marker, String nextPath) {
    String next = nextPath == null ? "" : "<a rel=\"next\" href=\"" + nextPath + "\">next</a>";
    return "<html><body><div class=\"body\">" + marker + "</div>" + next + "</body></html>";
  }

  private static void write(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
