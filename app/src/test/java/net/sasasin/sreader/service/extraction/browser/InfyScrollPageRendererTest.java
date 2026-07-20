package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class InfyScrollPageRendererTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsInvalidExtensionAndUserDataConfiguration() throws Exception {
    Path file = Files.createFile(temporaryDirectory.resolve("extension-file"));
    Path missing = temporaryDirectory.resolve("missing");
    Path okDir = Files.createDirectory(temporaryDirectory.resolve("ok"));
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);

    for (FeedReaderProperties.Playwright settings :
        List.of(
            settings(null, okDir),
            settings(missing, okDir),
            settings(file, okDir),
            settings(okDir, null))) {
      InfyScrollPageRenderer renderer = renderer(settings, runtime);
      assertThatIllegalStateException()
          .isThrownBy(() -> renderer.render(URI.create("https://example.test/")));
      verify(runtime, never()).launchPersistentContext(any(), any());
    }
  }

  @Test
  void launchesWithExtensionArgsReusesContextAndClosesOnlyPages() throws Exception {
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension"));
    Path userData = temporaryDirectory.resolve("user-data");
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    BrowserContext context = mock(BrowserContext.class);
    Page extensionPage = mock(Page.class);
    Page normalPage = mock(Page.class);
    Page rendered = mock(Page.class);
    when(runtime.launchPersistentContext(any(), any())).thenReturn(context);
    when(extensionPage.url()).thenReturn("chrome-extension://abc/options.html");
    when(normalPage.url()).thenReturn("https://example.test/");
    when(context.pages()).thenReturn(List.of(extensionPage, normalPage));
    when(context.newPage()).thenReturn(rendered);
    when(rendered.url()).thenReturn("https://example.test/final");
    when(rendered.content()).thenReturn("infy");
    stubInfyEvaluations(rendered, List.of(100L, 100L), List.of(0, 0));

    InfyScrollPageRenderer renderer = renderer(settings(extension, userData), runtime);

    assertThat(renderer.render(URI.create("https://example.test/start")).html()).isEqualTo("infy");
    assertThat(renderer.render(URI.create("https://example.test/start")).html()).isEqualTo("infy");

    ArgumentCaptor<BrowserType.LaunchPersistentContextOptions> options =
        ArgumentCaptor.forClass(BrowserType.LaunchPersistentContextOptions.class);
    verify(runtime).launchPersistentContext(any(), options.capture());
    assertThat(options.getValue().channel).isEqualTo("chromium");
    assertThat(options.getValue().headless).isTrue();
    assertThat(options.getValue().viewportSize).isPresent();
    assertThat(options.getValue().viewportSize.get().width).isEqualTo(800);
    String absolute = extension.toAbsolutePath().toString();
    assertThat(options.getValue().args)
        .containsExactly("--disable-extensions-except=" + absolute, "--load-extension=" + absolute);
    verify(extensionPage).close();
    verify(normalPage, never()).close();
    verify(rendered, times(2)).close();
    verify(context, never()).close();

    renderer.close();
    verify(context).close();

    when(runtime.launchPersistentContext(any(), any())).thenReturn(context);
    when(context.pages()).thenReturn(List.of());
    assertThat(renderer.render(URI.create("https://example.test/start")).html()).isEqualTo("infy");
    verify(runtime, times(2)).launchPersistentContext(any(), any());
  }

  @Test
  void closeWhenNoContextIsNoOp() {
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    InfyScrollPageRenderer renderer = renderer(settings(null, null), runtime);
    renderer.close();
    verify(runtime, never()).launchPersistentContext(any(), any());
  }

  @Test
  void nullUrlPageIsNotClosedDuringExtensionCleanup() throws Exception {
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension-null-url"));
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    BrowserContext context = mock(BrowserContext.class);
    Page nullUrlPage = mock(Page.class);
    Page rendered = mock(Page.class);
    when(runtime.launchPersistentContext(any(), any())).thenReturn(context);
    when(nullUrlPage.url()).thenReturn(null);
    when(context.pages()).thenReturn(List.of(nullUrlPage));
    when(context.newPage()).thenReturn(rendered);
    when(rendered.url()).thenReturn("https://example.test/");
    when(rendered.content()).thenReturn("ok");
    stubInfyEvaluations(rendered, List.of(100L, 100L), List.of(0, 0));

    InfyScrollPageRenderer renderer = renderer(settings(extension, temporaryDirectory), runtime);
    assertThat(renderer.render(URI.create("https://example.test/")).html()).isEqualTo("ok");
    verify(nullUrlPage, never()).close();
  }

  @Test
  void pageCloseFailureAfterSuccessIsPropagated() throws Exception {
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension-close"));
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    BrowserContext context = mock(BrowserContext.class);
    Page page = mock(Page.class);
    when(runtime.launchPersistentContext(any(), any())).thenReturn(context);
    when(context.pages()).thenReturn(List.of());
    when(context.newPage()).thenReturn(page);
    when(page.url()).thenReturn("https://example.test/");
    when(page.content()).thenReturn("ok");
    stubInfyEvaluations(page, List.of(100L, 100L), List.of(0, 0));
    doThrow(new RuntimeException("page close")).when(page).close();

    InfyScrollPageRenderer renderer = renderer(settings(extension, temporaryDirectory), runtime);
    assertThatThrownBy(() -> renderer.render(URI.create("https://example.test/")))
        .hasMessage("page close");
  }

  @Test
  void closesPageOnNavigationFailureAndDoesNotCloseContext() throws Exception {
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension"));
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    BrowserContext context = mock(BrowserContext.class);
    Page page = mock(Page.class);
    when(runtime.launchPersistentContext(any(), any())).thenReturn(context);
    when(context.pages()).thenReturn(List.of());
    when(context.newPage()).thenReturn(page);
    doThrow(new RuntimeException("navigate")).when(page).navigate(anyString(), any());

    InfyScrollPageRenderer renderer = renderer(settings(extension, temporaryDirectory), runtime);

    assertThatThrownBy(() -> renderer.render(URI.create("https://example.test/")))
        .hasMessage("navigate");
    verify(page).close();
    verify(context, never()).close();
  }

  @Test
  void extensionPageCleanupFailureClosesContextAndDoesNotCache() throws Exception {
    Path extension = Files.createDirectory(temporaryDirectory.resolve("extension"));
    PlaywrightRuntime runtime = mock(PlaywrightRuntime.class);
    BrowserContext context = mock(BrowserContext.class);
    Page extensionPage = mock(Page.class);
    when(runtime.launchPersistentContext(any(), any())).thenReturn(context);
    when(extensionPage.url()).thenReturn("chrome-extension://abc/x");
    when(context.pages()).thenReturn(List.of(extensionPage));
    doThrow(new RuntimeException("ext close")).when(extensionPage).close();

    InfyScrollPageRenderer renderer = renderer(settings(extension, temporaryDirectory), runtime);

    assertThatThrownBy(() -> renderer.render(URI.create("https://example.test/")))
        .hasMessage("ext close");
    verify(context).close();

    // Next render re-attempts launch.
    when(context.pages()).thenReturn(List.of());
    Page page = mock(Page.class);
    when(context.newPage()).thenReturn(page);
    when(page.url()).thenReturn("https://example.test/");
    when(page.content()).thenReturn("ok");
    stubInfyEvaluations(page, List.of(100L, 100L), List.of(0, 0));
    assertThat(renderer.render(URI.create("https://example.test/")).html()).isEqualTo("ok");
    verify(runtime, times(2)).launchPersistentContext(any(), any());
  }

  private InfyScrollPageRenderer renderer(
      FeedReaderProperties.Playwright settings, PlaywrightRuntime runtime) {
    PlaywrightPageNavigator navigator = new PlaywrightPageNavigator(settings);
    InfyScrollDriver driver = new InfyScrollDriver(settings, navigator);
    return new InfyScrollPageRenderer(settings, runtime, navigator, driver);
  }

  private static FeedReaderProperties.Playwright settings(Path extension, Path userData) {
    return new FeedReaderProperties.Playwright(
        true,
        true,
        800,
        600,
        Duration.ofSeconds(3),
        Duration.ofSeconds(2),
        extension,
        userData,
        3,
        1,
        Duration.ofMillis(10));
  }

  private static void stubInfyEvaluations(Page page, List<Object> heights, List<Object> dividers) {
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
