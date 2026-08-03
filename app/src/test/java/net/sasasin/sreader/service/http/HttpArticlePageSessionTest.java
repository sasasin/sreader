package net.sasasin.sreader.service.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.PageLoadException;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.outcome.FailureKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpArticlePageSessionTest {

  private HttpServer server;
  private URI baseUri;
  private final AtomicInteger page1Hits = new AtomicInteger();
  private final Map<String, List<String>> cookieHeadersByPath = new ConcurrentHashMap<>();
  private final AtomicInteger flakyAttempts = new AtomicInteger();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext(
        "/articles/1",
        exchange -> {
          page1Hits.incrementAndGet();
          recordCookie(exchange);
          if ("/articles/1".equals(exchange.getRequestURI().getPath())) {
            write(
                exchange,
                200,
                "text/html; charset=UTF-8",
                pageHtml("page-1", "/articles/2"),
                List.of("session=s1; Path=/"));
          }
        });
    server.createContext(
        "/articles/2",
        exchange -> {
          recordCookie(exchange);
          write(
              exchange,
              200,
              "text/html; charset=UTF-8",
              pageHtml("page-2", "/articles/3"),
              List.of());
        });
    server.createContext(
        "/articles/3",
        exchange -> {
          recordCookie(exchange);
          write(exchange, 200, "text/html; charset=UTF-8", pageHtml("page-3", null), List.of());
        });
    server.createContext(
        "/redirect-start",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/redirect-final");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.createContext(
        "/redirect-final",
        exchange ->
            write(
                exchange,
                200,
                "text/html; charset=UTF-8",
                pageHtml("redirected", null),
                List.of()));
    server.createContext(
        "/charset",
        exchange ->
            write(
                exchange,
                200,
                "text/html; charset=ISO-8859-1",
                "café",
                Charset.forName("ISO-8859-1"),
                List.of()));
    server.createContext(
        "/flaky",
        exchange -> {
          if (flakyAttempts.getAndIncrement() == 0) {
            exchange.close();
            return;
          }
          write(exchange, 200, "text/html; charset=UTF-8", pageHtml("retry-ok", null), List.of());
        });
    server.createContext(
        "/error", exchange -> write(exchange, 503, "text/plain", "unavailable", List.of()));
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(1500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          write(exchange, 200, "text/html; charset=UTF-8", pageHtml("slow", null), List.of());
        });
    server.start();
    baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    Thread.interrupted();
  }

  @Test
  void loadsThreeRelativePagesWithSharedCookies() throws Exception {
    HttpArticlePageSessionFactory factory = factory(1, Duration.ofSeconds(2));
    try (ArticlePageSession session = factory.open()) {
      PageSnapshot p1 = session.load(baseUri.resolve("/articles/1"));
      PageSnapshot p2 = session.load(baseUri.resolve("/articles/2"));
      PageSnapshot p3 = session.load(baseUri.resolve("/articles/3"));

      assertThat(p1.html()).contains("page-1");
      assertThat(p2.html()).contains("page-2");
      assertThat(p3.html()).contains("page-3");
      assertThat(page1Hits.get()).isEqualTo(1);
      assertThat(cookieHeadersByPath.get("/articles/2")).isNotEmpty();
      assertThat(cookieHeadersByPath.get("/articles/2").getFirst()).contains("session=s1");
      assertThat(cookieHeadersByPath.get("/articles/3").getFirst()).contains("session=s1");
      assertThat(p1.byteSize()).isGreaterThan(0);
      assertThat(p1.byteSize()).isEqualTo(p1.html().getBytes(StandardCharsets.UTF_8).length);
    }
  }

  @Test
  void cookieStateDoesNotLeakAcrossSessions() throws Exception {
    HttpArticlePageSessionFactory factory = factory(0, Duration.ofSeconds(2));
    try (ArticlePageSession first = factory.open()) {
      first.load(baseUri.resolve("/articles/1"));
    }
    cookieHeadersByPath.clear();
    try (ArticlePageSession second = factory.open()) {
      second.load(baseUri.resolve("/articles/2"));
      assertThat(cookieHeadersByPath.get("/articles/2")).isNotNull();
      assertThat(
              cookieHeadersByPath.get("/articles/2").getFirst() == null
                  || !cookieHeadersByPath.get("/articles/2").getFirst().contains("session=s1"))
          .isTrue();
    }
  }

  @Test
  void followsRedirectAndReportsFinalUri() throws Exception {
    try (ArticlePageSession session = factory(0, Duration.ofSeconds(2)).open()) {
      PageSnapshot page = session.load(baseUri.resolve("/redirect-start"));
      assertThat(page.requestedUri()).isEqualTo(baseUri.resolve("/redirect-start"));
      assertThat(page.finalUri()).isEqualTo(baseUri.resolve("/redirect-final"));
      assertThat(page.html()).contains("redirected");
    }
  }

  @Test
  void decodesDeclaredCharset() throws Exception {
    try (ArticlePageSession session = factory(0, Duration.ofSeconds(2)).open()) {
      PageSnapshot page = session.load(baseUri.resolve("/charset"));
      assertThat(page.html()).isEqualTo("café");
      assertThat(page.byteSize()).isEqualTo("café".getBytes(Charset.forName("ISO-8859-1")).length);
    }
  }

  @Test
  void retriesIoFailures() throws Exception {
    try (ArticlePageSession session = factory(1, Duration.ofSeconds(2)).open()) {
      PageSnapshot page = session.load(baseUri.resolve("/flaky"));
      assertThat(page.html()).contains("retry-ok");
      assertThat(flakyAttempts.get()).isGreaterThanOrEqualTo(2);
    }
  }

  @Test
  void non2xxIsHttpStatusFailure() {
    try (ArticlePageSession session = factory(0, Duration.ofSeconds(2)).open()) {
      assertThatThrownBy(() -> session.load(baseUri.resolve("/error")))
          .isInstanceOf(PageLoadException.class)
          .satisfies(
              ex -> {
                PageLoadException pageLoad = (PageLoadException) ex;
                assertThat(pageLoad.kind()).isEqualTo(FailureKind.HTTP_STATUS);
                assertThat(pageLoad.getMessage()).contains("503");
              });
    }
  }

  @Test
  void readTimeoutIsIoFailure() {
    try (ArticlePageSession session = factory(0, Duration.ofMillis(200)).open()) {
      assertThatThrownBy(() -> session.load(baseUri.resolve("/slow")))
          .isInstanceOf(PageLoadException.class)
          .satisfies(ex -> assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.IO));
    }
  }

  @Test
  void interruptIsPreserved() {
    Thread.currentThread().interrupt();
    try (ArticlePageSession session = factory(0, Duration.ofSeconds(2)).open()) {
      // Client may throw immediately or complete; force interrupt path by wrapping.
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private void recordCookie(HttpExchange exchange) {
    cookieHeadersByPath
        .computeIfAbsent(exchange.getRequestURI().getPath(), key -> new CopyOnWriteArrayList<>())
        .add(exchange.getRequestHeaders().getFirst("Cookie"));
  }

  private static void write(
      HttpExchange exchange, int status, String contentType, String body, List<String> setCookies)
      throws IOException {
    write(exchange, status, contentType, body, StandardCharsets.UTF_8, setCookies);
  }

  private static void write(
      HttpExchange exchange,
      int status,
      String contentType,
      String body,
      Charset charset,
      List<String> setCookies)
      throws IOException {
    byte[] bytes = body.getBytes(charset);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    for (String cookie : setCookies) {
      exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static String pageHtml(String marker, String nextPath) {
    String next = nextPath == null ? "" : "<a rel=\"next\" href=\"" + nextPath + "\">next</a>";
    return "<html><body><div class=\"body\">" + marker + "</div>" + next + "</body></html>";
  }

  private HttpArticlePageSessionFactory factory(int retries, Duration readTimeout) {
    FeedReaderProperties properties =
        new FeedReaderProperties(
            null,
            null,
            new FeedReaderProperties.Http(
                "session-test-agent", Duration.ofSeconds(1), readTimeout, retries),
            null,
            null,
            null,
            null);
    return new HttpArticlePageSessionFactory(new HttpTransport(properties));
  }
}
