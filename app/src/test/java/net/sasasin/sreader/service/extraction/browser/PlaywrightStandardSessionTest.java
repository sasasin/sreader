package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.PageLoadException;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import org.junit.jupiter.api.Test;

class PlaywrightStandardSessionTest {

  @Test
  void withStandardSessionClosesContextAfterWork() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/");
    when(f.page.content()).thenReturn("<html/>");

    String html =
        f.source.withStandardSession(
            session -> {
              try {
                PageSnapshot snapshot = session.load(URI.create("https://example.test/"));
                return snapshot.html();
              } catch (PageLoadException e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(html).isEqualTo("<html/>");
    verify(f.context).close();
  }

  @Test
  void withStandardSessionClosesContextWhenWorkFails() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/");
    when(f.page.content()).thenReturn("<html/>");

    assertThatThrownBy(
            () ->
                f.source.withStandardSession(
                    session -> {
                      throw new IllegalStateException("work boom");
                    }))
        .hasMessage("work boom");
    verify(f.context).close();
  }

  @Test
  void withStandardSessionPreservesPrimaryWhenCloseFails() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/");
    when(f.page.content()).thenReturn("<html/>");
    doThrow(new RuntimeException("close failed")).when(f.context).close();

    Throwable thrown =
        org.assertj.core.api.Assertions.catchThrowable(
            () ->
                f.source.withStandardSession(
                    session -> {
                      throw new IllegalStateException("work boom");
                    }));
    assertThat(thrown).hasMessage("work boom");
    assertThat(thrown.getSuppressed()).hasSize(1);
    assertThat(thrown.getSuppressed()[0]).hasMessage("close failed");
  }

  @Test
  void disabledSourceRejectsWithStandardSession() {
    PlaywrightHtmlSource source =
        new PlaywrightHtmlSource(
            properties(false),
            mock(PlaywrightResourceLifecycle.class),
            mock(StandardPlaywrightPageRenderer.class));

    assertThatIllegalStateException()
        .isThrownBy(() -> source.withStandardSession(session -> "x"))
        .withMessageContaining("disabled");
  }

  @Test
  void withStandardSessionRejectsNullWork() {
    Fixture f = fixture();
    assertThatThrownBy(() -> f.source.withStandardSession(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("work");
  }

  @Test
  void withStandardSessionPropagatesCloseFailureAfterSuccess() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/");
    when(f.page.content()).thenReturn("<html/>");
    doThrow(new RuntimeException("close failed")).when(f.context).close();

    assertThatThrownBy(
            () ->
                f.source.withStandardSession(
                    session -> {
                      try {
                        return session.load(URI.create("https://example.test/")).html();
                      } catch (PageLoadException e) {
                        throw new RuntimeException(e);
                      }
                    }))
        .hasMessage("close failed");
  }

  @Test
  void serializesSessionWorkWithSinglePageRender() throws Exception {
    CountDownLatch sessionEntered = new CountDownLatch(1);
    CountDownLatch releaseSession = new CountDownLatch(1);
    CountDownLatch renderRequested = new CountDownLatch(1);
    CountDownLatch renderEntered = new CountDownLatch(1);
    AtomicInteger concurrent = new AtomicInteger();
    AtomicInteger maxConcurrent = new AtomicInteger();

    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    when(standard.withSession(any()))
        .thenAnswer(
            invocation -> {
              PlaywrightSessionWork<?> work = invocation.getArgument(0);
              concurrent.incrementAndGet();
              maxConcurrent.updateAndGet(v -> Math.max(v, concurrent.get()));
              sessionEntered.countDown();
              if (!releaseSession.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("session release timeout");
              }
              concurrent.decrementAndGet();
              ArticlePageSession session = mock(ArticlePageSession.class);
              return work.apply(session);
            });
    when(standard.render(any()))
        .thenAnswer(
            invocation -> {
              renderEntered.countDown();
              concurrent.incrementAndGet();
              maxConcurrent.updateAndGet(v -> Math.max(v, concurrent.get()));
              concurrent.decrementAndGet();
              return new RenderedPage(URI.create("https://example.test/"), "ok");
            });

    PlaywrightHtmlSource source =
        new PlaywrightHtmlSource(
            properties(true), mock(PlaywrightResourceLifecycle.class), standard);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> sessionFuture =
          executor.submit(() -> source.withStandardSession(session -> "session-done"));
      assertThat(sessionEntered.await(5, TimeUnit.SECONDS)).isTrue();
      Future<RenderedPage> renderFuture =
          executor.submit(
              () -> {
                renderRequested.countDown();
                return source.renderPage(URI.create("https://example.test/"));
              });
      assertThat(renderRequested.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(renderEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();
      assertThat(maxConcurrent.get()).isEqualTo(1);
      releaseSession.countDown();
      assertThat(sessionFuture.get(5, TimeUnit.SECONDS)).isEqualTo("session-done");
      assertThat(renderFuture.get(5, TimeUnit.SECONDS).html()).isEqualTo("ok");
      assertThat(maxConcurrent.get()).isEqualTo(1);
      verify(standard).withSession(any());
      verify(standard).render(any());
    } finally {
      releaseSession.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void twoChainsUseTwoContexts() {
    Fixture f = fixture();
    BrowserContext context2 = mock(BrowserContext.class);
    Page page2 = mock(Page.class);
    when(f.browser.newContext(any())).thenReturn(f.context, context2);
    when(f.context.newPage()).thenReturn(f.page);
    when(context2.newPage()).thenReturn(page2);
    when(f.page.url()).thenReturn("https://example.test/a");
    when(f.page.content()).thenReturn("<a/>");
    when(page2.url()).thenReturn("https://example.test/b");
    when(page2.content()).thenReturn("<b/>");

    f.source.withStandardSession(session -> loadHtml(session, "https://example.test/a"));
    f.source.withStandardSession(session -> loadHtml(session, "https://example.test/b"));

    verify(f.browser, times(2)).newContext(any());
    verify(f.context).close();
    verify(context2).close();
  }

  private static String loadHtml(ArticlePageSession session, String uri) {
    try {
      return session.load(URI.create(uri)).html();
    } catch (PageLoadException e) {
      throw new RuntimeException(e);
    }
  }

  private static FeedReaderProperties properties(boolean enabled) {
    return new FeedReaderProperties(null, null, null, settings(enabled), null, null, List.of());
  }

  private static FeedReaderProperties.Playwright settings(boolean enabled) {
    return new FeedReaderProperties.Playwright(
        enabled, true, 800, 600, Duration.ofSeconds(3), Duration.ofSeconds(2));
  }

  private Fixture fixture() {
    FeedReaderProperties.Playwright playwrightSettings = settings(true);
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    Browser browser = mock(Browser.class);
    BrowserContext context = mock(BrowserContext.class);
    Page page = mock(Page.class);
    when(runtime.browser()).thenReturn(browser);
    when(browser.newContext(any())).thenReturn(context);
    when(context.newPage()).thenReturn(page);
    PlaywrightPageNavigator navigator = new PlaywrightPageNavigator(playwrightSettings);
    StandardPlaywrightPageRenderer standard =
        new StandardPlaywrightPageRenderer(playwrightSettings, runtime, navigator);
    PlaywrightHtmlSource source =
        new PlaywrightHtmlSource(
            properties(true), mock(PlaywrightResourceLifecycle.class), standard);
    return new Fixture(source, browser, context, page);
  }

  private record Fixture(
      PlaywrightHtmlSource source, Browser browser, BrowserContext context, Page page) {}
}
