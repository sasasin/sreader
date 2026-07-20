package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PlaywrightResourceLifecycleTest {

  @Test
  void disabledStartDoesNotStartRuntime() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    InfyScrollPageRenderer infy = mock(InfyScrollPageRenderer.class);
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings(false), runtime, infy);

    lifecycle.start();

    verify(runtime, never()).start();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void enabledStartDelegatesToRuntime() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    when(runtime.isRunning()).thenReturn(true);
    InfyScrollPageRenderer infy = mock(InfyScrollPageRenderer.class);
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings(true), runtime, infy);

    lifecycle.start();

    verify(runtime).start();
    verify(infy, never()).close();
    assertThat(lifecycle.isRunning()).isTrue();
  }

  @Test
  void stopClosesInfyThenRuntimeEvenWhenInfyFails() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    InfyScrollPageRenderer infy = mock(InfyScrollPageRenderer.class);
    RuntimeException infyFailure = new RuntimeException("infy close");
    RuntimeException runtimeFailure = new RuntimeException("runtime stop");
    doThrow(infyFailure).when(infy).close();
    doThrow(runtimeFailure).when(runtime).stop();
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings(true), runtime, infy);

    assertThatThrownBy(lifecycle::stop)
        .isSameAs(infyFailure)
        .hasSuppressedException(runtimeFailure);

    InOrder order = inOrder(infy, runtime);
    order.verify(infy).close();
    order.verify(runtime).stop();
  }

  @Test
  void stopPropagatesRuntimeFailureWhenInfySucceeds() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    InfyScrollPageRenderer infy = mock(InfyScrollPageRenderer.class);
    RuntimeException runtimeFailure = new RuntimeException("runtime stop");
    doThrow(runtimeFailure).when(runtime).stop();
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings(true), runtime, infy);

    assertThatThrownBy(lifecycle::stop).isSameAs(runtimeFailure);
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
}
