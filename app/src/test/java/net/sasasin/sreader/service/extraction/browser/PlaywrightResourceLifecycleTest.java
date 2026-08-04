package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;

class PlaywrightResourceLifecycleTest {

  @Test
  void disabledStartDoesNotStartRuntime() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings(false), runtime);

    lifecycle.start();

    verify(runtime, never()).start();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void enabledStartDelegatesToRuntime() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    when(runtime.isRunning()).thenReturn(true);
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings(true), runtime);

    lifecycle.start();

    verify(runtime).start();
    assertThat(lifecycle.isRunning()).isTrue();
  }

  @Test
  void stopPropagatesRuntimeFailure() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    RuntimeException runtimeFailure = new RuntimeException("runtime stop");
    doThrow(runtimeFailure).when(runtime).stop();
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings(true), runtime);

    assertThatThrownBy(lifecycle::stop).isSameAs(runtimeFailure);
  }

  private static FeedReaderProperties.Playwright settings(boolean enabled) {
    return new FeedReaderProperties.Playwright(
        enabled, true, 800, 600, Duration.ofSeconds(3), Duration.ofSeconds(2));
  }
}
