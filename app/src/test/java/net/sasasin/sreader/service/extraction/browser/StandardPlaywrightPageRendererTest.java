package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
// assertThatThrownBy already imported via Assertions

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.time.Duration;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StandardPlaywrightPageRendererTest {

  @Test
  void createsViewportContextNavigatesAndClosesPerRender() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/final");
    when(f.page.content()).thenReturn("<main>ok</main>");

    RenderedPage first = f.renderer.render(URI.create("https://example.test/start"));
    RenderedPage second = f.renderer.render(URI.create("https://example.test/start"));

    assertThat(first.html()).isEqualTo("<main>ok</main>");
    assertThat(second.finalUri()).isEqualTo(URI.create("https://example.test/final"));
    verify(f.browser, times(2)).newContext(any());
    verify(f.context, times(2)).close();

    ArgumentCaptor<Browser.NewContextOptions> options =
        ArgumentCaptor.forClass(Browser.NewContextOptions.class);
    verify(f.browser, times(2)).newContext(options.capture());
    assertThat(options.getValue().viewportSize).isPresent();
    assertThat(options.getValue().viewportSize.get().width).isEqualTo(800);
    assertThat(options.getValue().viewportSize.get().height).isEqualTo(600);
  }

  @Test
  void closesContextOnNavigationFailure() {
    Fixture f = fixture();
    doThrow(new RuntimeException("navigation")).when(f.page).navigate(any(), any());

    assertThatThrownBy(() -> f.renderer.render(URI.create("https://example.test/")))
        .hasMessage("navigation");
    verify(f.context).close();
  }

  @Test
  void closesContextOnCaptureFailure() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/");
    when(f.page.content()).thenThrow(new RuntimeException("content"));

    assertThatThrownBy(() -> f.renderer.render(URI.create("https://example.test/")))
        .hasMessage("content");
    verify(f.context).close();
  }

  @Test
  void closesContextOnPageCreationFailure() {
    Fixture f = fixture();
    when(f.context.newPage()).thenThrow(new RuntimeException("page"));

    assertThatThrownBy(() -> f.renderer.render(URI.create("https://example.test/")))
        .hasMessage("page");
    verify(f.context).close();
  }

  @Test
  void usesRuntimeBrowserWhichLazyStarts() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/");
    when(f.page.content()).thenReturn("ok");
    f.renderer.render(URI.create("https://example.test/"));
    verify(f.runtime).browser();
  }

  @Test
  void closeFailureAfterSuccessIsPropagated() {
    Fixture f = fixture();
    when(f.page.url()).thenReturn("https://example.test/");
    when(f.page.content()).thenReturn("ok");
    doThrow(new RuntimeException("close failed")).when(f.context).close();

    assertThatThrownBy(() -> f.renderer.render(URI.create("https://example.test/")))
        .hasMessage("close failed");
  }

  @Test
  void closeFailureIsSuppressedOntoPrimaryFailure() {
    Fixture f = fixture();
    doThrow(new RuntimeException("navigation")).when(f.page).navigate(any(), any());
    doThrow(new RuntimeException("close failed")).when(f.context).close();

    Throwable thrown =
        org.assertj.core.api.Assertions.catchThrowable(
            () -> f.renderer.render(URI.create("https://example.test/")));
    assertThat(thrown).hasMessage("navigation");
    assertThat(thrown.getSuppressed()).hasSize(1);
    assertThat(thrown.getSuppressed()[0]).hasMessage("close failed");
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
    return new Fixture(
        new StandardPlaywrightPageRenderer(settings, runtime, navigator),
        runtime,
        browser,
        context,
        page);
  }

  private record Fixture(
      StandardPlaywrightPageRenderer renderer,
      PlaywrightRuntime runtime,
      Browser browser,
      BrowserContext context,
      Page page) {}
}
