package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.time.Duration;
import java.util.function.Supplier;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class PlaywrightRuntimeTest {

  @Test
  void startLaunchesChromiumWithHeadlessAndIsIdempotent() {
    Fixture f = fixture();
    PlaywrightRuntime runtime = f.runtime();

    runtime.start();
    runtime.start();

    assertThat(runtime.isRunning()).isTrue();
    verify(f.factory()).get();
    ArgumentCaptor<BrowserType.LaunchOptions> options =
        ArgumentCaptor.forClass(BrowserType.LaunchOptions.class);
    verify(f.chromium()).launch(options.capture());
    assertThat(options.getValue().channel).isEqualTo("chromium");
    assertThat(options.getValue().headless).isTrue();
  }

  @Test
  void browserAndChromiumLazyStart() {
    Fixture f = fixture();
    PlaywrightRuntime runtime = f.runtime();

    assertThat(runtime.browser()).isSameAs(f.browser());
    assertThat(runtime.chromium()).isSameAs(f.chromium());
    verify(f.factory()).get();
  }

  @Test
  void stopClosesBrowserThenPlaywrightAndAllowsRestart() {
    Fixture f = fixture();
    PlaywrightRuntime runtime = f.runtime();
    runtime.start();

    runtime.stop();
    runtime.stop();
    assertThat(runtime.isRunning()).isFalse();

    InOrder order = inOrder(f.browser(), f.playwright());
    order.verify(f.browser()).close();
    order.verify(f.playwright()).close();

    runtime.start();
    verify(f.factory(), times(2)).get();
    assertThat(runtime.isRunning()).isTrue();
  }

  @Test
  void factoryFailureLeavesCleanState() {
    @SuppressWarnings("unchecked")
    Supplier<Playwright> factory = mock();
    when(factory.get()).thenThrow(new RuntimeException("create failed"));
    PlaywrightRuntime runtime = new PlaywrightRuntime(settings(true), factory);

    assertThatThrownBy(runtime::start).hasMessage("create failed");
    assertThat(runtime.isRunning()).isFalse();
  }

  @Test
  void browserLaunchFailureClosesPlaywright() {
    Fixture f = fixture();
    RuntimeException launchFailure = new RuntimeException("launch failed");
    when(f.chromium().launch(any())).thenThrow(launchFailure);

    assertThatThrownBy(f.runtime()::start).isSameAs(launchFailure);
    verify(f.playwright()).close();
    assertThat(f.runtime().isRunning()).isFalse();
  }

  @Test
  void browserCloseFailureStillClosesPlaywrightAndSuppresses() {
    Fixture f = fixture();
    PlaywrightRuntime runtime = f.runtime();
    runtime.start();
    RuntimeException browserClose = new RuntimeException("browser close");
    RuntimeException playwrightClose = new RuntimeException("playwright close");
    doThrow(browserClose).when(f.browser()).close();
    doThrow(playwrightClose).when(f.playwright()).close();

    assertThatThrownBy(runtime::stop)
        .isSameAs(browserClose)
        .hasSuppressedException(playwrightClose);
    assertThat(runtime.isRunning()).isFalse();
    verify(f.playwright()).close();
  }

  @Test
  void stopWhenNeverStartedIsNoOp() {
    @SuppressWarnings("unchecked")
    Supplier<Playwright> factory = mock();
    PlaywrightRuntime runtime = new PlaywrightRuntime(settings(true), factory);
    runtime.stop();
    verify(factory, never()).get();
  }

  @Test
  void browserLaunchFailureWithCloseFailureSuppresses() {
    Fixture f = fixture();
    RuntimeException launchFailure = new RuntimeException("launch failed");
    RuntimeException closeFailure = new RuntimeException("close failed");
    when(f.chromium().launch(any())).thenThrow(launchFailure);
    doThrow(closeFailure).when(f.playwright()).close();

    assertThatThrownBy(f.runtime()::start)
        .isSameAs(launchFailure)
        .hasSuppressedException(closeFailure);
    assertThat(f.runtime().isRunning()).isFalse();
  }

  private static FeedReaderProperties.Playwright settings(boolean headless) {
    return new FeedReaderProperties.Playwright(
        true,
        headless,
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
  private Fixture fixture() {
    Supplier<Playwright> factory = mock();
    Playwright playwright = mock(Playwright.class);
    BrowserType chromium = mock(BrowserType.class);
    Browser browser = mock(Browser.class);
    when(factory.get()).thenReturn(playwright);
    when(playwright.chromium()).thenReturn(chromium);
    when(chromium.launch(any())).thenReturn(browser);
    return new Fixture(
        new PlaywrightRuntime(settings(true), factory), factory, playwright, chromium, browser);
  }

  private record Fixture(
      PlaywrightRuntime runtime,
      Supplier<Playwright> factory,
      Playwright playwright,
      BrowserType chromium,
      Browser browser) {}
}
