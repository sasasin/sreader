package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.autopagerize.PageLoadException;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.outcome.FailureKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaywrightArticlePageSessionTest {

  @Test
  void reusesSameContextAndPageAcrossThreeNavigations() throws Exception {
    Fixture f = fixture();
    when(f.page.url())
        .thenReturn("https://example.test/p1")
        .thenReturn("https://example.test/p2")
        .thenReturn("https://example.test/p3");
    when(f.page.content())
        .thenReturn("<html>one</html>")
        .thenReturn("<html>two</html>")
        .thenReturn("<html>three</html>");

    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);

    PageSnapshot first = session.load(URI.create("https://example.test/p1"));
    PageSnapshot second = session.load(URI.create("https://example.test/p2"));
    PageSnapshot third = session.load(URI.create("https://example.test/p3"));

    assertThat(first.finalUri()).isEqualTo(URI.create("https://example.test/p1"));
    assertThat(first.html()).isEqualTo("<html>one</html>");
    assertThat(first.byteSize())
        .isEqualTo("<html>one</html>".getBytes(StandardCharsets.UTF_8).length);
    assertThat(second.html()).isEqualTo("<html>two</html>");
    assertThat(third.finalUri()).isEqualTo(URI.create("https://example.test/p3"));

    verify(f.browser, times(1)).newContext(any());
    verify(f.context, times(1)).newPage();
    verify(f.page, times(3)).navigate(any(String.class), any());
    session.close();
    verify(f.context, times(1)).close();
  }

  @Test
  void separateOpenCallsCreateSeparateContexts() {
    Fixture f = fixture();
    BrowserContext context2 = mock(BrowserContext.class);
    Page page2 = mock(Page.class);
    when(f.browser.newContext(any())).thenReturn(f.context, context2);
    when(f.context.newPage()).thenReturn(f.page);
    when(context2.newPage()).thenReturn(page2);

    PlaywrightArticlePageSession first =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);
    PlaywrightArticlePageSession second =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);

    assertThat(first).isNotSameAs(second);
    verify(f.browser, times(2)).newContext(any());
    first.close();
    second.close();
    verify(f.context).close();
    verify(context2).close();
  }

  @Test
  void openAppliesViewportSettings() {
    Fixture f = fixture();
    PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator).close();

    ArgumentCaptor<Browser.NewContextOptions> options =
        ArgumentCaptor.forClass(Browser.NewContextOptions.class);
    verify(f.browser).newContext(options.capture());
    assertThat(options.getValue().viewportSize).isPresent();
    assertThat(options.getValue().viewportSize.get().width).isEqualTo(800);
    assertThat(options.getValue().viewportSize.get().height).isEqualTo(600);
  }

  @Test
  void openClosesContextWhenPageCreationFails() {
    Fixture f = fixture();
    when(f.context.newPage()).thenThrow(new RuntimeException("page boom"));

    assertThatThrownBy(() -> PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator))
        .hasMessage("page boom");
    verify(f.context).close();
  }

  @Test
  void loadFailureUsesRenderKindAndNetworkIdleIsBestEffort() {
    Fixture f = fixture();
    doThrow(new RuntimeException("nav fail")).when(f.page).navigate(any(String.class), any());
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);

    assertThatThrownBy(() -> session.load(URI.create("https://example.test/")))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            error -> {
              PageLoadException load = (PageLoadException) error;
              assertThat(load.kind()).isEqualTo(FailureKind.RENDER);
              assertThat(load.getMessage()).contains("nav fail");
            });
  }

  @Test
  void networkIdleTimeoutDoesNotFailLoad() throws Exception {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/final");
    when(f.page.content()).thenReturn("<html>ok</html>");
    // waitForLoadState throws (simulates network idle timeout); navigator swallows it.
    doThrow(new RuntimeException("timeout")).when(f.page).waitForLoadState(any(), any());

    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);
    PageSnapshot snapshot = session.load(URI.create("https://example.test/start"));

    assertThat(snapshot.finalUri()).isEqualTo(URI.create("https://example.test/final"));
    assertThat(snapshot.html()).isEqualTo("<html>ok</html>");
  }

  @Test
  void closedSessionRejectsLoad() {
    Fixture f = fixture();
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);
    session.close();

    assertThatThrownBy(() -> session.load(URI.create("https://example.test/")))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            error ->
                assertThat(((PageLoadException) error).kind())
                    .isEqualTo(FailureKind.INVALID_INPUT));
  }

  @Test
  void closeFailureIsPropagated() {
    Fixture f = fixture();
    doThrow(new RuntimeException("close failed")).when(f.context).close();
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);

    assertThatThrownBy(session::close).hasMessage("close failed");
  }

  @Test
  void closeIsIdempotentAfterSuccess() {
    Fixture f = fixture();
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);
    session.close();
    session.close();
    verify(f.context, times(1)).close();
  }

  @Test
  void interruptedThreadMapsLoadToInterruptedKind() {
    Fixture f = fixture();
    doThrow(new RuntimeException("nav")).when(f.page).navigate(any(String.class), any());
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> session.load(URI.create("https://example.test/")))
          .isInstanceOf(PageLoadException.class)
          .satisfies(
              error ->
                  assertThat(((PageLoadException) error).kind())
                      .isEqualTo(FailureKind.INTERRUPTED));
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void interruptedExceptionCauseMapsLoadToInterruptedKind() {
    Fixture f = fixture();
    RuntimeException wrapped = new RuntimeException("wrap", new InterruptedException("stop"));
    doThrow(wrapped).when(f.page).navigate(any(String.class), any());
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);

    assertThatThrownBy(() -> session.load(URI.create("https://example.test/")))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            error ->
                assertThat(((PageLoadException) error).kind()).isEqualTo(FailureKind.INTERRUPTED));
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    Thread.interrupted();
  }

  @Test
  void blankAndNullExceptionMessagesUseClassName() {
    Fixture f = fixture();
    doThrow(new RuntimeException("   ")).when(f.page).navigate(any(String.class), any());
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator);

    assertThatThrownBy(() -> session.load(URI.create("https://example.test/")))
        .isInstanceOf(PageLoadException.class)
        .hasMessageContaining("RuntimeException");

    doThrow(new RuntimeException((String) null)).when(f.page).navigate(any(String.class), any());
    assertThatThrownBy(() -> session.load(URI.create("https://example.test/")))
        .isInstanceOf(PageLoadException.class)
        .hasMessageContaining("RuntimeException");
  }

  @Test
  void openPreservesPrimaryWhenContextCloseAlsoFails() {
    Fixture f = fixture();
    when(f.context.newPage()).thenThrow(new RuntimeException("page boom"));
    doThrow(new RuntimeException("close boom")).when(f.context).close();

    Throwable thrown =
        org.assertj.core.api.Assertions.catchThrowable(
            () -> PlaywrightArticlePageSession.open(f.runtime, f.settings, f.navigator));
    assertThat(thrown).hasMessage("page boom");
    assertThat(thrown.getSuppressed()).hasSize(1);
    assertThat(thrown.getSuppressed()[0]).hasMessage("close boom");
  }

  private Fixture fixture() {
    FeedReaderProperties.Playwright settings =
        new FeedReaderProperties.Playwright(
            true,
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
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    Browser browser = mock(Browser.class);
    BrowserContext context = mock(BrowserContext.class);
    Page page = mock(Page.class);
    when(runtime.browser()).thenReturn(browser);
    when(browser.newContext(any())).thenReturn(context);
    when(context.newPage()).thenReturn(page);
    PlaywrightPageNavigator navigator = new PlaywrightPageNavigator(settings);
    return new Fixture(settings, runtime, browser, context, page, navigator);
  }

  private record Fixture(
      FeedReaderProperties.Playwright settings,
      PlaywrightRuntime runtime,
      Browser browser,
      BrowserContext context,
      Page page,
      PlaywrightPageNavigator navigator) {}
}
