package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import java.util.Arrays;
import java.util.List;
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
    Supplier<Playwright> factory = mock();
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(properties(false, null, null, 2, 2), factory);

    service.start();

    assertThat(service.isRunning()).isFalse();
    assertThat(service.isAutoStartup()).isFalse();
    assertThatIllegalStateException()
        .isThrownBy(() -> service.renderPage("https://example.test", false))
        .withMessageContaining("disabled");
    verify(factory, never()).get();
  }

  @Test
  void startIsIdempotentAndStopAllowsRestart() {
    Started started = started();
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(properties(true, null, null, 2, 2), started.factory());

    service.start();
    service.start();
    assertThat(service.isRunning()).isTrue();
    verify(started.factory()).get();
    verify(started.chromium()).launch(any());

    service.stop();
    service.stop();
    assertThat(service.isRunning()).isFalse();
    InOrder order = inOrder(started.browser(), started.playwright());
    order.verify(started.browser()).close();
    order.verify(started.playwright()).close();

    service.start();
    verify(started.factory(), times(2)).get();
  }

  @Test
  void regularRenderLazyStartsReturnsContentAndClosesContext() {
    Started started = started();
    Page page = mock(Page.class);
    when(started.context().newPage()).thenReturn(page);
    when(page.url()).thenReturn("https://example.test/final");
    when(page.content()).thenReturn("<main>ok</main>");
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(properties(true, null, null, 2, 2), started.factory());

    assertThat(service.render("https://example.test/start", false)).isEqualTo("<main>ok</main>");
    assertThat(service.renderPage("https://example.test/start", false))
        .isEqualTo(new RenderedPage(URI.create("https://example.test/final"), "<main>ok</main>"));
    verify(started.factory()).get();
    verify(started.browser(), times(2)).newContext(any());
    verify(page, times(2)).navigate(anyString(), any());
    verify(page, times(2)).waitForLoadState(any(), any());
    verify(started.context(), times(2)).close();
  }

  @Test
  void regularRenderContinuesAfterNetworkIdleFailureAndClosesOnNavigationFailure() {
    Started started = started();
    Page page = mock(Page.class);
    when(started.context().newPage()).thenReturn(page);
    doThrow(new RuntimeException("still polling")).when(page).waitForLoadState(any(), any());
    when(page.url()).thenReturn(" ");
    when(page.content()).thenReturn("ok");
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(properties(true, null, null, 2, 2), started.factory());
    assertThat(service.renderPage("https://example.test/fallback", false).finalUri())
        .isEqualTo(URI.create("https://example.test/fallback"));

    Page broken = mock(Page.class);
    when(started.context().newPage()).thenReturn(broken);
    doThrow(new RuntimeException("navigation")).when(broken).navigate(anyString(), any());
    assertThatThrownBy(() -> service.renderPage("https://example.test", false))
        .isInstanceOf(RuntimeException.class);
    verify(started.context(), times(2)).close();
  }

  @Test
  void regularRenderFallsBackToNullForInvalidUrisAndClosesOnContentFailure() {
    Started started = started();
    Page page = mock(Page.class);
    when(started.context().newPage()).thenReturn(page);
    when(page.url()).thenReturn("bad uri[]");
    when(page.content()).thenReturn("ok");
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(properties(true, null, null, 2, 2), started.factory());
    assertThat(service.renderPage("also bad[]", false).finalUri()).isNull();

    when(page.url()).thenReturn(null);
    when(page.content()).thenThrow(new RuntimeException("content"));
    assertThatThrownBy(() -> service.renderPage("https://example.test", false))
        .isInstanceOf(RuntimeException.class);
    verify(started.context(), times(2)).close();
  }

  @Test
  void infyConfigurationRejectsInvalidDirectories() throws Exception {
    Started started = started();
    Path file = Files.createFile(temporaryDirectory.resolve("extension-file"));
    for (FeedReaderProperties properties :
        List.of(
            properties(true, null, temporaryDirectory, 2, 2),
            properties(true, temporaryDirectory.resolve("missing"), temporaryDirectory, 2, 2),
            properties(true, file, temporaryDirectory, 2, 2),
            properties(true, temporaryDirectory, null, 2, 2))) {
      PlaywrightHtmlSource service = new PlaywrightHtmlSource(properties, started.factory());
      assertThatIllegalStateException()
          .isThrownBy(() -> service.renderPage("https://example.test", true));
    }
  }

  @Test
  void infyRenderReusesPersistentContextCleansExtensionPagesAndClosesOnlyPages() throws Exception {
    Started started = started();
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension"));
    Page extensionPage = mock(Page.class);
    Page normalPage = mock(Page.class);
    Page rendered = mock(Page.class);
    when(extensionPage.url()).thenReturn("chrome-extension://abc/options.html");
    when(normalPage.url()).thenReturn("https://example.test/");
    when(started.infyContext().pages()).thenReturn(List.of(extensionPage, normalPage));
    when(started.infyContext().newPage()).thenReturn(rendered);
    when(rendered.url()).thenReturn("https://example.test/final");
    when(rendered.content()).thenReturn("infy");
    stubInfyEvaluations(rendered, List.of(100L, 100L, 100L), List.of(0, 0, 0));
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            properties(true, extension, temporaryDirectory, 3, 1), started.factory());

    assertThat(service.renderPage("https://example.test/start", true).html()).isEqualTo("infy");
    assertThat(service.renderPage("https://example.test/start", true).html()).isEqualTo("infy");
    verify(started.chromium()).launchPersistentContext(any(), any());
    verify(extensionPage).close();
    verify(normalPage, never()).close();
    verify(rendered, times(2)).close();
    verify(started.infyContext(), never()).close();
    service.stop();
    verify(started.infyContext()).close();
  }

  @Test
  void infyScrollHandlesGrowthMaximumRoundsNonNumbersAndPageFailures() throws Exception {
    Started started = started();
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension"));
    Page page = mock(Page.class);
    when(started.infyContext().pages()).thenReturn(List.of());
    when(started.infyContext().newPage()).thenReturn(page);
    when(page.url()).thenReturn("https://example.test/");
    when(page.content()).thenReturn("ok");
    stubInfyEvaluations(
        page, Arrays.asList(null, 100L, 150L, 200L), List.of("not number", 1, 2, 3));
    PlaywrightHtmlSource service =
        new PlaywrightHtmlSource(
            properties(true, extension, temporaryDirectory, 2, 3), started.factory());

    service.renderPage("https://example.test", true);
    verify(page, times(2)).waitForTimeout(10);

    Page broken = mock(Page.class);
    when(started.infyContext().newPage()).thenReturn(broken);
    doThrow(new RuntimeException("navigate")).when(broken).navigate(anyString(), any());
    assertThatThrownBy(() -> service.renderPage("https://example.test", true))
        .isInstanceOf(RuntimeException.class);
    verify(broken).close();
  }

  private void stubInfyEvaluations(Page page, List<Object> heights, List<Object> dividers) {
    AtomicInteger height = new AtomicInteger();
    AtomicInteger divider = new AtomicInteger();
    doAnswer(
            invocation -> {
              String script = invocation.getArgument(0);
              if (script.contains("querySelectorAll")) {
                return dividers.get(Math.min(divider.getAndIncrement(), dividers.size() - 1));
              }
              if (script.contains("Math.max") && !script.contains("window.scrollTo")) {
                return heights.get(Math.min(height.getAndIncrement(), heights.size() - 1));
              }
              return null;
            })
        .when(page)
        .evaluate(anyString());
  }

  private FeedReaderProperties properties(
      boolean enabled, Path extension, Path userData, int maxScrolls, int stableRounds) {
    return new FeedReaderProperties(
        null,
        null,
        null,
        new FeedReaderProperties.Playwright(
            enabled,
            true,
            800,
            600,
            Duration.ofSeconds(3),
            Duration.ofSeconds(2),
            extension,
            userData,
            maxScrolls,
            stableRounds,
            Duration.ofMillis(10)),
        null,
        null);
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
