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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

class PlaywrightHtmlSourceTest {

  @TempDir Path temporaryDirectory;

  @Test
  void disabledServiceDoesNotStartAndCannotRender() {
    PlaywrightResourceLifecycle lifecycle = mock(PlaywrightResourceLifecycle.class);
    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    InfyScrollPageRenderer infy = mock(InfyScrollPageRenderer.class);
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(settings(false), lifecycle, standard, infy);

    service.start();
    verify(lifecycle).start();
    assertThat(service.isAutoStartup()).isFalse();
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                service.renderPage(
                    URI.create("https://example.test"), PlaywrightRenderMode.STANDARD))
        .withMessageContaining("disabled");
    verify(standard, never()).render(any());
  }

  @Test
  void dispatchesByModeAndRenderReturnsHtml() {
    PlaywrightResourceLifecycle lifecycle = mock(PlaywrightResourceLifecycle.class);
    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    InfyScrollPageRenderer infy = mock(InfyScrollPageRenderer.class);
    URI uri = URI.create("https://example.test/");
    when(standard.render(uri)).thenReturn(new RenderedPage(uri, "<standard>"));
    when(infy.render(uri)).thenReturn(new RenderedPage(uri, "<infy>"));
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(settings(true), lifecycle, standard, infy);

    assertThat(service.render(uri, PlaywrightRenderMode.STANDARD)).isEqualTo("<standard>");
    assertThat(service.renderPage(uri, PlaywrightRenderMode.INFY_SCROLL).html())
        .isEqualTo("<infy>");
    verify(standard).render(uri);
    verify(infy).render(uri);
  }

  @Test
  void rejectsNullUriAndMode() {
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            settings(true),
            mock(PlaywrightResourceLifecycle.class),
            mock(StandardPlaywrightPageRenderer.class),
            mock(InfyScrollPageRenderer.class));
    assertThatNullPointerException()
        .isThrownBy(() -> service.renderPage(null, PlaywrightRenderMode.STANDARD));
    assertThatNullPointerException()
        .isThrownBy(() -> service.renderPage(URI.create("https://example.test/"), null));
  }

  @Test
  void lifecycleDelegates() {
    PlaywrightResourceLifecycle lifecycle = mock(PlaywrightResourceLifecycle.class);
    when(lifecycle.isRunning()).thenReturn(true);
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            settings(true),
            lifecycle,
            mock(StandardPlaywrightPageRenderer.class),
            mock(InfyScrollPageRenderer.class));

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
            settings(true),
            mock(PlaywrightResourceLifecycle.class),
            standard,
            mock(InfyScrollPageRenderer.class));

    assertThatThrownBy(
            () ->
                service.renderPage(
                    URI.create("https://example.test/"), PlaywrightRenderMode.STANDARD))
        .hasMessage("boom");
  }

  @Test
  void serializesConcurrentRenders() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger concurrent = new AtomicInteger();
    AtomicInteger maxConcurrent = new AtomicInteger();
    StandardPlaywrightPageRenderer standard = mock(StandardPlaywrightPageRenderer.class);
    when(standard.render(any()))
        .thenAnswer(
            invocation -> {
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
            settings(true),
            mock(PlaywrightResourceLifecycle.class),
            standard,
            mock(InfyScrollPageRenderer.class));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(
              () ->
                  service.renderPage(
                      URI.create("https://example.test/1"), PlaywrightRenderMode.STANDARD));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> second =
          executor.submit(
              () ->
                  service.renderPage(
                      URI.create("https://example.test/2"), PlaywrightRenderMode.STANDARD));
      // While first render holds the monitor, second must not enter renderer.
      Thread.sleep(50);
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

    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            settings(true), lifecycle, standard, mock(InfyScrollPageRenderer.class));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> renderFuture =
          executor.submit(
              () ->
                  service.renderPage(
                      URI.create("https://example.test/"), PlaywrightRenderMode.STANDARD));
      assertThat(inRender.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> stopFuture = executor.submit(() -> service.stop());
      // stop must not run while render holds the monitor
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
    PlaywrightHtmlSource service = new PlaywrightHtmlSource(settings(true), started.factory());

    assertThat(
            service.render(URI.create("https://example.test/start"), PlaywrightRenderMode.STANDARD))
        .isEqualTo("<main>ok</main>");
    verify(started.factory()).get();
    verify(started.context()).close();
  }

  @Test
  void integrationStopClosesInfyThenBrowserThenPlaywright() throws Exception {
    Started started = started();
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension"));
    when(started.infyContext().pages()).thenReturn(List.of());
    Page rendered = mock(Page.class);
    when(started.infyContext().newPage()).thenReturn(rendered);
    when(rendered.url()).thenReturn("https://example.test/");
    when(rendered.content()).thenReturn("infy");
    doAnswer(
            invocation -> {
              String script = invocation.getArgument(0);
              if (script.contains("querySelectorAll") && script.contains("infy-scroll-divider")) {
                return 0;
              }
              if (script.contains("Math.max") && !script.contains("window.scrollTo")) {
                return 100L;
              }
              return null;
            })
        .when(rendered)
        .evaluate(any(String.class));

    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            new FeedReaderProperties.Playwright(
                true,
                true,
                800,
                600,
                Duration.ofSeconds(3),
                Duration.ofSeconds(2),
                extension,
                temporaryDirectory,
                1,
                1,
                Duration.ofMillis(10)),
            started.factory());

    service.renderPage(URI.create("https://example.test/"), PlaywrightRenderMode.INFY_SCROLL);
    service.stop();

    InOrder order = inOrder(started.infyContext(), started.browser(), started.playwright());
    order.verify(started.infyContext()).close();
    order.verify(started.browser()).close();
    order.verify(started.playwright()).close();
  }

  private static FeedReaderProperties.Playwright settings(boolean enabled) {
    return new FeedReaderProperties.Playwright(
        enabled,
        true,
        800,
        600,
        Duration.ofSeconds(3),
        Duration.ofSeconds(2),
        null,
        null,
        2,
        2,
        Duration.ofMillis(10));
  }

  @SuppressWarnings("unchecked")
  private Started started() {
    Supplier<Playwright> factory = mock();
    Playwright playwright = mock(Playwright.class);
    BrowserType chromium = mock(BrowserType.class);
    Browser browser = mock(Browser.class);
    BrowserContext context = mock(BrowserContext.class);
    BrowserContext infyContext = mock(BrowserContext.class);
    when(factory.get()).thenReturn(playwright);
    when(playwright.chromium()).thenReturn(chromium);
    when(chromium.launch(any())).thenReturn(browser);
    when(browser.newContext(any())).thenReturn(context);
    when(chromium.launchPersistentContext(any(), any())).thenReturn(infyContext);
    return new Started(factory, playwright, chromium, browser, context, infyContext);
  }

  private record Started(
      Supplier<Playwright> factory,
      Playwright playwright,
      BrowserType chromium,
      Browser browser,
      BrowserContext context,
      BrowserContext infyContext) {}
}
