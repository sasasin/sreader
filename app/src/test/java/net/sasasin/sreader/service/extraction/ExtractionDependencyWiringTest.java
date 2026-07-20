package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ExtractionDependencyWiringTest {

  @Autowired private ApplicationContext context;
  @Autowired private HtmlTextExtractor extractor;
  @Autowired private ReadabilityParser readabilityParser;
  @Autowired private ExtractRuleService extractRuleService;

  @Test
  void extractorUsesManagedReadabilityParser() {
    assertThat(context.getBeansOfType(ReadabilityParser.class)).hasSize(1);
    assertThat(context.getBeansOfType(ReadabilityArticleParser.class)).hasSize(1);
    assertThat(context.getBeansOfType(HtmlTextExtractor.class)).hasSize(1);
    assertThat(readabilityParser).isInstanceOf(ReadabilityArticleParser.class);
    assertThat(ReflectionTestUtils.getField(extractor, "readabilityParser"))
        .isSameAs(readabilityParser);
    assertThat(ReflectionTestUtils.getField(extractor, "extractRuleService"))
        .isSameAs(extractRuleService);
  }
}
