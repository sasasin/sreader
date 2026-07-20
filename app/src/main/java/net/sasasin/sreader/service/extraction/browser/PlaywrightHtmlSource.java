package net.sasasin.sreader.service.extraction.browser;

import java.net.URI;
import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;
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

  PlaywrightHtmlSource(
      FeedReaderProperties properties,
      PlaywrightResourceLifecycle lifecycle,
      StandardPlaywrightPageRenderer standardRenderer,
      InfyScrollPageRenderer infyRenderer) {
    this.settings = Objects.requireNonNull(properties, "properties must not be null").playwright();
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
    this.standardRenderer =
        Objects.requireNonNull(standardRenderer, "standardRenderer must not be null");
    this.infyRenderer = Objects.requireNonNull(infyRenderer, "infyRenderer must not be null");
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
}
