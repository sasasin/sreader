package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.microsoft.playwright.Page;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;

class InfyScrollDriverTest {

  @Test
  void stopsAfterConfiguredStableRoundsWithoutMaxingOut() {
    Page page = mock(Page.class);
    stubEvaluations(page, List.of(100L, 100L, 100L), List.of(0, 0, 0));
    InfyScrollDriver driver = driver(3, 2);

    driver.drive(page);

    verify(page, times(2)).waitForTimeout(10);
  }

  @Test
  void heightGrowthOf21ResetsStableAndExactly20DoesNot() {
    Page page = mock(Page.class);
    // initial 100; after scroll1: 120 (+20, not growth); scroll2: 141 (+21 growth); then stable
    stubEvaluations(page, List.of(100L, 120L, 141L, 141L, 141L), List.of(0, 0, 0, 0, 0));
    InfyScrollDriver driver = driver(10, 2);

    driver.drive(page);

    // rounds: no-growth, growth-reset, no-growth, no-growth-stop => 4 waits
    verify(page, times(4)).waitForTimeout(10);
  }

  @Test
  void dividerGrowthResetsStableRounds() {
    Page page = mock(Page.class);
    stubEvaluations(page, List.of(100L, 100L, 100L, 100L), List.of(0, 1, 1, 1));
    InfyScrollDriver driver = driver(10, 2);

    driver.drive(page);

    // growth on first round, then two stable => 3 waits
    verify(page, times(3)).waitForTimeout(10);
  }

  @Test
  void maxScrollsStopsEvenWhenGrowing() {
    Page page = mock(Page.class);
    stubEvaluations(page, List.of(100L, 200L, 300L, 400L), List.of(0, 1, 2, 3));
    InfyScrollDriver driver = driver(2, 10);

    driver.drive(page);

    verify(page, times(2)).waitForTimeout(10);
  }

  @Test
  void nonNumberHeightAndDividerBecomeZero() {
    Page page = mock(Page.class);
    stubEvaluations(page, Arrays.asList(null, "x", "y"), Arrays.asList("not number", null, "z"));
    InfyScrollDriver driver = driver(2, 2);

    driver.drive(page);

    verify(page, times(2)).waitForTimeout(10);
  }

  @Test
  void cleanupUsesInfySelectorsAndPropagatesFailure() {
    Page page = mock(Page.class);
    InfyScrollDriver driver = driver(1, 1);

    driver.cleanup(page);
    verify(page).evaluate(org.mockito.ArgumentMatchers.contains("infy-scroll-loading"));

    doThrow(new RuntimeException("cleanup")).when(page).evaluate(anyString());
    assertThatThrownBy(() -> driver.cleanup(page)).hasMessage("cleanup");
  }

  private static InfyScrollDriver driver(int maxScrolls, int stableRounds) {
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
            maxScrolls,
            stableRounds,
            Duration.ofMillis(10));
    PlaywrightPageNavigator navigator = new PlaywrightPageNavigator(settings);
    return new InfyScrollDriver(settings, navigator);
  }

  private static void stubEvaluations(Page page, List<Object> heights, List<Object> dividers) {
    AtomicInteger height = new AtomicInteger();
    AtomicInteger divider = new AtomicInteger();
    doAnswer(
            invocation -> {
              String script = invocation.getArgument(0);
              if (script.contains("querySelectorAll") && script.contains("infy-scroll-divider")) {
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
}
