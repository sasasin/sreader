package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.time.Duration;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaywrightPageNavigatorTest {

  private final PlaywrightPageNavigator navigator = new PlaywrightPageNavigator(settings());

  @Test
  void navigateUsesDomContentLoadedAndTimeout() {
    Page page = mock(Page.class);
    URI uri = URI.create("https://example.test/start");

    navigator.navigate(page, uri);

    ArgumentCaptor<Page.NavigateOptions> options =
        ArgumentCaptor.forClass(Page.NavigateOptions.class);
    verify(page).navigate(eq("https://example.test/start"), options.capture());
    assertThat(options.getValue().waitUntil).isEqualTo(WaitUntilState.DOMCONTENTLOADED);
    assertThat(options.getValue().timeout).isEqualTo(3000.0);
  }

  @Test
  void networkIdleAbsorbsRuntimeException() {
    Page page = mock(Page.class);
    doThrow(new RuntimeException("still polling")).when(page).waitForLoadState(any(), any());

    navigator.waitNetworkIdleBestEffort(page);

    ArgumentCaptor<Page.WaitForLoadStateOptions> options =
        ArgumentCaptor.forClass(Page.WaitForLoadStateOptions.class);
    verify(page).waitForLoadState(eq(LoadState.NETWORKIDLE), options.capture());
    assertThat(options.getValue().timeout).isEqualTo(2000.0);
  }

  @Test
  void captureUsesPageUrlAndContent() {
    Page page = mock(Page.class);
    when(page.url()).thenReturn("https://example.test/final");
    when(page.content()).thenReturn("<main>ok</main>");

    RenderedPage rendered = navigator.capture(page, URI.create("https://example.test/start"));

    assertThat(rendered)
        .isEqualTo(new RenderedPage(URI.create("https://example.test/final"), "<main>ok</main>"));
  }

  @Test
  void finalUriFallsBackForNullBlankAndInvalidPageUrl() {
    URI requested = URI.create("https://example.test/fallback");
    assertThat(navigator.resolveFinalUri(null, requested)).isEqualTo(requested);
    assertThat(navigator.resolveFinalUri(" ", requested)).isEqualTo(requested);
    assertThat(navigator.resolveFinalUri("bad uri[]", requested)).isEqualTo(requested);
  }

  @Test
  void capturePropagatesContentFailure() {
    Page page = mock(Page.class);
    when(page.url()).thenReturn("https://example.test/");
    when(page.content()).thenThrow(new RuntimeException("content"));

    assertThatThrownBy(() -> navigator.capture(page, URI.create("https://example.test/")))
        .hasMessage("content");
  }

  @Test
  void rejectsNullPageAndUri() {
    Page page = mock(Page.class);
    assertThatNullPointerException()
        .isThrownBy(() -> navigator.navigate(null, URI.create("https://example.test/")));
    assertThatNullPointerException().isThrownBy(() -> navigator.navigate(page, null));
    assertThatNullPointerException()
        .isThrownBy(() -> navigator.capture(null, URI.create("https://example.test/")));
  }

  private static FeedReaderProperties.Playwright settings() {
    return new FeedReaderProperties.Playwright(
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
  }
}
