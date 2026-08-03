package net.sasasin.sreader.service.autopagerize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class AutoPagerizeDependencyWiringTest {

  @Autowired private ApplicationContext context;

  @Test
  void autopagerizeBeansAreRegistered() {
    assertThat(context.getBean(AutoPagerizeImportService.class)).isNotNull();
    assertThat(context.getBean(AutoPagerizeImportPersister.class)).isNotNull();
    assertThat(context.getBean(AutoPagerizeJsonParser.class)).isNotNull();
    assertThat(context.getBean(AutoPagerizeRuleCatalog.class)).isNotNull();
    assertThat(context.getBean(AutoPagerizeUrlPatternCompiler.class)).isNotNull();
    assertThat(context.getBean(AutoPagerizeXPathSyntaxChecker.class)).isNotNull();
  }
}
