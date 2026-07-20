package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Playwright;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring composition root for the Playwright browser resource graph. Bean construction does not
 * start the Playwright process; {@link PlaywrightRuntime} remains lazy until start/render.
 */
@Configuration(proxyBeanMethods = false)
class PlaywrightBrowserConfiguration {

  @Bean
  PlaywrightFactory playwrightFactory() {
    return Playwright::create;
  }

  @Bean
  PlaywrightRuntime playwrightRuntime(FeedReaderProperties properties, PlaywrightFactory factory) {
    return new PlaywrightRuntime(properties.playwright(), factory);
  }

  @Bean
  PlaywrightPageNavigator playwrightPageNavigator(FeedReaderProperties properties) {
    return new PlaywrightPageNavigator(properties.playwright());
  }

  @Bean
  InfyScrollDriver infyScrollDriver(
      FeedReaderProperties properties, PlaywrightPageNavigator navigator) {
    return new InfyScrollDriver(properties.playwright(), navigator);
  }

  @Bean
  InfyScrollPageRenderer infyScrollPageRenderer(
      FeedReaderProperties properties,
      PlaywrightRuntime runtime,
      PlaywrightPageNavigator navigator,
      InfyScrollDriver driver) {
    return new InfyScrollPageRenderer(properties.playwright(), runtime, navigator, driver);
  }

  @Bean
  StandardPlaywrightPageRenderer standardPlaywrightPageRenderer(
      FeedReaderProperties properties,
      PlaywrightRuntime runtime,
      PlaywrightPageNavigator navigator) {
    return new StandardPlaywrightPageRenderer(properties.playwright(), runtime, navigator);
  }

  @Bean
  PlaywrightResourceLifecycle playwrightResourceLifecycle(
      FeedReaderProperties properties,
      PlaywrightRuntime runtime,
      InfyScrollPageRenderer infyRenderer) {
    return new PlaywrightResourceLifecycle(properties.playwright(), runtime, infyRenderer);
  }
}
