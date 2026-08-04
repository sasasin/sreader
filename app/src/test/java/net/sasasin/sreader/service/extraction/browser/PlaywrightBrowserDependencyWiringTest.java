package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class PlaywrightBrowserDependencyWiringTest {

  @Autowired private ApplicationContext context;
  @Autowired private PlaywrightHtmlSource facade;
  @Autowired private PlaywrightRuntime runtime;
  @Autowired private PlaywrightPageNavigator navigator;
  @Autowired private StandardPlaywrightPageRenderer standardRenderer;
  @Autowired private PlaywrightResourceLifecycle lifecycle;
  @Autowired private PlaywrightFactory factory;

  @Test
  void browserGraphIsSingletonAndShared() {
    assertThat(context.getBeansOfType(PlaywrightFactory.class)).hasSize(1);
    assertThat(context.getBeansOfType(PlaywrightRuntime.class)).hasSize(1);
    assertThat(context.getBeansOfType(PlaywrightPageNavigator.class)).hasSize(1);
    assertThat(context.getBeansOfType(StandardPlaywrightPageRenderer.class)).hasSize(1);
    assertThat(context.getBeansOfType(PlaywrightResourceLifecycle.class)).hasSize(1);
    assertThat(context.getBeansOfType(PlaywrightHtmlSource.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(Object.class))
        .noneMatch(name -> name.toLowerCase().contains("infy"));

    assertThat(ReflectionTestUtils.getField(facade, "lifecycle")).isSameAs(lifecycle);
    assertThat(ReflectionTestUtils.getField(facade, "standardRenderer")).isSameAs(standardRenderer);
    assertThat(ReflectionTestUtils.getField(standardRenderer, "runtime")).isSameAs(runtime);
    assertThat(ReflectionTestUtils.getField(lifecycle, "runtime")).isSameAs(runtime);
    assertThat(ReflectionTestUtils.getField(standardRenderer, "navigator")).isSameAs(navigator);
    assertThat(ReflectionTestUtils.getField(runtime, "playwrightFactory")).isSameAs(factory);
  }

  @Test
  void contextStartupDoesNotStartPlaywrightResources() {
    assertThat(runtime.isRunning()).isFalse();
    assertThat(facade.isRunning()).isFalse();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void onlyFacadeIsSmartLifecycleAmongBrowserBeans() {
    List<SmartLifecycle> browserLifecycles =
        context.getBeansOfType(SmartLifecycle.class).entrySet().stream()
            .filter(
                entry ->
                    context
                        .getType(entry.getKey())
                        .getPackageName()
                        .equals(PlaywrightHtmlSource.class.getPackageName()))
            .map(Map.Entry::getValue)
            .toList();

    assertThat(browserLifecycles).containsExactly(facade);
  }
}
