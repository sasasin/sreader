package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PlaywrightHtmlSourceTest {

  @Test
  void disabledServiceDoesNotStartAndCannotRender() {
    PlaywrightResourceLifecycle lifecycle = mock(PlaywrightResourceLifecycle.class);
    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    PlaywrightHtmlSource service = new PlaywrightHtmlSource(properties(false), lifecycle, standard);

    service.start();
    verify(lifecycle).start();
    assertThat(service.isAutoStartup()).isFalse();
    assertThatIllegalStateException()
        .isThrownBy(() -> service.renderPage(URI.create("https://example.test")))
        .withMessageContaining("disabled");
    verify(standard, never()).render(any());
  }

  @Test
  void renderDelegatesToStandardRenderer() {
    PlaywrightResourceLifecycle lifecycle = mock(PlaywrightResourceLifecycle.class);
    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    URI uri = URI.create("https://example.test/");
    when(standard.render(uri)).thenReturn(new RenderedPage(uri, "<standard>"));
    PlaywrightHtmlSource service = new PlaywrightHtmlSource(properties(true), lifecycle, standard);

    assertThat(service.render(uri)).isEqualTo("<standard>");
    assertThat(service.renderPage(uri).html()).isEqualTo("<standard>");
    verify(standard, times(2)).render(uri);
  }

  @Test
  void rejectsNullUri() {
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            properties(true),
            mock(PlaywrightResourceLifecycle.class),
            mock(StandardPlaywrightPageRenderer.class));
    assertThatNullPointerException().isThrownBy(() -> service.renderPage(null));
  }

  @Test
  void lifecycleDelegates() {
    PlaywrightResourceLifecycle lifecycle = mock(PlaywrightResourceLifecycle.class);
    when(lifecycle.isRunning()).thenReturn(true);
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            properties(true), lifecycle, mock(StandardPlaywrightPageRenderer.class));

    service.start();
    service.stop();
    assertThat(service.isRunning()).isTrue();
    verify(lifecycle).start();
    verify(lifecycle).stop();
  }

  @Test
  void propagatesRendererFailure() {
    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    when(standard.render(any())).thenThrow(new RuntimeException("boom"));
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            properties(true), mock(PlaywrightResourceLifecycle.class), standard);

    assertThatThrownBy(() -> service.renderPage(URI.create("https://example.test/")))
        .hasMessage("boom");
  }

  @Test
  void serializesConcurrentRenders() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch secondRenderRequested = new CountDownLatch(1);
    CountDownLatch secondRendererEntered = new CountDownLatch(1);
    AtomicInteger concurrent = new AtomicInteger();
    AtomicInteger maxConcurrent = new AtomicInteger();
    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    when(standard.render(any()))
        .thenAnswer(
            invocation -> {
              URI requestedUri = invocation.getArgument(0);
              if (requestedUri.getPath().equals("/2")) {
                secondRendererEntered.countDown();
              }
              concurrent.incrementAndGet();
              maxConcurrent.updateAndGet(v -> Math.max(v, concurrent.get()));
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release timeout");
              }
              concurrent.decrementAndGet();
              return new RenderedPage(URI.create("https://example.test/"), "ok");
            });
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            properties(true), mock(PlaywrightResourceLifecycle.class), standard);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(() -> service.renderPage(URI.create("https://example.test/1")));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> second =
          executor.submit(
              () -> {
                secondRenderRequested.countDown();
                return service.renderPage(URI.create("https://example.test/2"));
              });
      assertThat(secondRenderRequested.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(secondRendererEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();
      assertThat(maxConcurrent.get()).isEqualTo(1);
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
      assertThat(maxConcurrent.get()).isEqualTo(1);
      verify(standard, times(2)).render(any());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void stopWaitsForActiveRender() throws Exception {
    CountDownLatch inRender = new CountDownLatch(1);
    CountDownLatch releaseRender = new CountDownLatch(1);
    CountDownLatch stopEntered = new CountDownLatch(1);
    AtomicInteger order = new AtomicInteger();
    AtomicInteger renderOrder = new AtomicInteger();
    AtomicInteger stopOrder = new AtomicInteger();

    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    when(standard.render(any()))
        .thenAnswer(
            invocation -> {
              renderOrder.set(order.incrementAndGet());
              inRender.countDown();
              if (!releaseRender.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("render release timeout");
              }
              return new RenderedPage(URI.create("https://example.test/"), "ok");
            });
    PlaywrightResourceLifecycle lifecycle = mock(PlaywrightResourceLifecycle.class);
    doAnswer(
            invocation -> {
              stopOrder.set(order.incrementAndGet());
              stopEntered.countDown();
              return null;
            })
        .when(lifecycle)
        .stop();

    PlaywrightHtmlSource service = new PlaywrightHtmlSource(properties(true), lifecycle, standard);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> renderFuture =
          executor.submit(() -> service.renderPage(URI.create("https://example.test/")));
      assertThat(inRender.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> stopFuture = executor.submit(() -> service.stop());
      assertThat(stopEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();
      releaseRender.countDown();
      renderFuture.get(5, TimeUnit.SECONDS);
      stopFuture.get(5, TimeUnit.SECONDS);
      assertThat(stopEntered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(renderOrder.get()).isLessThan(stopOrder.get());
    } finally {
      releaseRender.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void integrationRegularRenderLazyStartsAndClosesContext() {
    Started started = started();
    Page page = mock(Page.class);
    when(started.context().newPage()).thenReturn(page);
    when(page.url()).thenReturn("https://example.test/final");
    when(page.content()).thenReturn("<main>ok</main>");
    PlaywrightHtmlSource service = source(settings(true), started.factory());

    assertThat(service.render(URI.create("https://example.test/start")))
        .isEqualTo("<main>ok</main>");
    verify(started.factory()).create();
    verify(started.context()).close();
  }

  @Test
  void integrationStopClosesBrowserThenPlaywright() {
    Started started = started();
    Page page = mock(Page.class);
    when(started.context().newPage()).thenReturn(page);
    when(page.url()).thenReturn("https://example.test/");
    when(page.content()).thenReturn("<main>ok</main>");
    PlaywrightHtmlSource service = source(settings(true), started.factory());

    service.renderPage(URI.create("https://example.test/"));
    service.stop();

    InOrder order = inOrder(started.browser(), started.playwright());
    order.verify(started.browser()).close();
    order.verify(started.playwright()).close();
  }

  private static FeedReaderProperties properties(boolean enabled) {
    return new FeedReaderProperties(null, null, null, settings(enabled), null, null, List.of());
  }

  private static FeedReaderProperties.Playwright settings(boolean enabled) {
    return new FeedReaderProperties.Playwright(
        enabled, true, 800, 600, Duration.ofSeconds(3), Duration.ofSeconds(2));
  }

  private static PlaywrightHtmlSource source(
      FeedReaderProperties.Playwright settings, PlaywrightFactory factory) {
    PlaywrightRuntime runtime = new PlaywrightRuntime(settings, factory);
    PlaywrightPageNavigator navigator = new PlaywrightPageNavigator(settings);
    StandardPlaywrightPageRenderer standard =
        new StandardPlaywrightPageRenderer(settings, runtime, navigator);
    PlaywrightResourceLifecycle lifecycle = new PlaywrightResourceLifecycle(settings, runtime);
    return new PlaywrightHtmlSource(
        new FeedReaderProperties(null, null, null, settings, null, null, List.of()),
        lifecycle,
        standard);
  }

  private Started started() {
    PlaywrightFactory factory = mock(PlaywrightFactory.class);
    Playwright playwright = mock(Playwright.class);
    BrowserType chromium = mock(BrowserType.class);
    Browser browser = mock(Browser.class);
    BrowserContext context = mock(BrowserContext.class);
    when(factory.create()).thenReturn(playwright);
    when(playwright.chromium()).thenReturn(chromium);
    when(chromium.launch(any())).thenReturn(browser);
    when(browser.newContext(any())).thenReturn(context);
    return new Started(factory, playwright, chromium, browser, context);
  }

  private record Started(
      PlaywrightFactory factory,
      Playwright playwright,
      BrowserType chromium,
      Browser browser,
      BrowserContext context) {}
}
