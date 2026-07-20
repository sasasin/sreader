package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.sasasin.sreader.service.canonicalization.ContentCanonicalizationMaintenanceService;
import net.sasasin.sreader.service.extraction.HtmlTextExtractor;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.feed.ingestion.FeedEntryImportService;
import net.sasasin.sreader.service.feed.toml.FeedTomlService;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Locks single-constructor injection conventions for P1-3 target facades. */
class ConstructorInjectionConventionTest {

  private static final List<Class<?>> TARGETS =
      List.of(
          FeedTomlService.class,
          ContentCanonicalizationMaintenanceService.class,
          FeedEntryImportService.class,
          FullTextProbeService.class,
          PlaywrightHtmlSource.class,
          HttpFetchService.class,
          HtmlTextExtractor.class);

  @Test
  void targetFacadesHaveSingleConstructorWithoutAutowired() {
    for (Class<?> type : TARGETS) {
      Constructor<?>[] constructors = type.getDeclaredConstructors();
      assertThat(constructors).as(type.getName()).hasSize(1);
      Constructor<?> constructor = constructors[0];
      assertThat(constructor.isAnnotationPresent(Autowired.class))
          .as("%s constructor must not use @Autowired", type.getName())
          .isFalse();
    }
  }

  @Test
  void targetFacadesDoNotUseFieldOrSetterInjection() {
    for (Class<?> type : TARGETS) {
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        assertThat(field.isAnnotationPresent(Autowired.class))
            .as("%s.%s", type.getName(), field.getName())
            .isFalse();
      }
      for (Method method : type.getDeclaredMethods()) {
        assertThat(method.isAnnotationPresent(Autowired.class))
            .as("%s.%s", type.getName(), method.getName())
            .isFalse();
      }
    }
  }
}
