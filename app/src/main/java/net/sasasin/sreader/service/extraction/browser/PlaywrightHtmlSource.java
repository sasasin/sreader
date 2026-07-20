package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Playwright;
import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

/**
 * Public facade for Playwright page rendering.
 *
 * <p>All public render/start/stop operations are synchronized on this instance so Playwright
 * operations and resource teardown never interleave.
 */
@Service
public class PlaywrightHtmlSource implements SmartLifecycle {

  private final FeedReaderProperties.Playwright settings;
  private final PlaywrightResourceLifecycle lifecycle;
  private final StandardPlaywrightPageRenderer standardRenderer;
  private final InfyScrollPageRenderer infyRenderer;

  @Autowired
  public PlaywrightHtmlSource(FeedReaderProperties properties) {
    this(properties.playwright(), Playwright::create);
  }

  PlaywrightHtmlSource(
      FeedReaderProperties.Playwright settings, Supplier<Playwright> playwrightFactory) {
    this(
        settings,
        buildCollaborators(
            Objects.requireNonNull(settings, "settings must not be null"),
            Objects.requireNonNull(playwrightFactory, "playwrightFactory must not be null")));
  }

  /** Test constructor that injects collaborators without rebuilding the resource graph. */
  PlaywrightHtmlSource(
      FeedReaderProperties.Playwright settings,
      PlaywrightResourceLifecycle lifecycle,
      StandardPlaywrightPageRenderer standardRenderer,
      InfyScrollPageRenderer infyRenderer) {
    this.settings = Objects.requireNonNull(settings, "settings must not be null");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
    this.standardRenderer =
        Objects.requireNonNull(standardRenderer, "standardRenderer must not be null");
    this.infyRenderer = Objects.requireNonNull(infyRenderer, "infyRenderer must not be null");
  }

  private PlaywrightHtmlSource(
      FeedReaderProperties.Playwright settings, Collaborators collaborators) {
    this(
        settings,
        collaborators.lifecycle(),
        collaborators.standardRenderer(),
        collaborators.infyRenderer());
  }

  private static Collaborators buildCollaborators(
      FeedReaderProperties.Playwright settings, Supplier<Playwright> playwrightFactory) {
    PlaywrightRuntime runtime = new PlaywrightRuntime(settings, playwrightFactory);
    PlaywrightPageNavigator navigator = new PlaywrightPageNavigator(settings);
    InfyScrollDriver driver = new InfyScrollDriver(settings, navigator);
    InfyScrollPageRenderer infyRenderer =
        new InfyScrollPageRenderer(settings, runtime, navigator, driver);
    StandardPlaywrightPageRenderer standardRenderer =
        new StandardPlaywrightPageRenderer(settings, runtime, navigator);
    PlaywrightResourceLifecycle lifecycle =
        new PlaywrightResourceLifecycle(settings, runtime, infyRenderer);
    return new Collaborators(lifecycle, standardRenderer, infyRenderer);
  }

  public synchronized String render(URI uri, PlaywrightRenderMode mode) {
    return renderPage(uri, mode).html();
  }

  public synchronized RenderedPage renderPage(URI uri, PlaywrightRenderMode mode) {
    Objects.requireNonNull(uri, "uri must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    if (!settings.enabled()) {
      throw new IllegalStateException("Playwright full text extraction is disabled");
    }
    return switch (mode) {
      case STANDARD -> standardRenderer.render(uri);
      case INFY_SCROLL -> infyRenderer.render(uri);
    };
  }

  @Override
  public synchronized void start() {
    lifecycle.start();
  }

  @Override
  public synchronized void stop() {
    lifecycle.stop();
  }

  @Override
  public boolean isRunning() {
    return lifecycle.isRunning();
  }

  @Override
  public boolean isAutoStartup() {
    return false;
  }

  private record Collaborators(
      PlaywrightResourceLifecycle lifecycle,
      StandardPlaywrightPageRenderer standardRenderer,
      InfyScrollPageRenderer infyRenderer) {}
}
