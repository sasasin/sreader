package net.sasasin.sreader.service.feed.toml;

import net.sasasin.sreader.repository.FeedUrlRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition root for feed TOML import/export collaborators. */
@Configuration(proxyBeanMethods = false)
class FeedTomlConfiguration {

  @Bean
  FeedTomlParser feedTomlParser() {
    return new FeedTomlParser();
  }

  @Bean
  FeedTomlSchemaValidator feedTomlSchemaValidator() {
    return new FeedTomlSchemaValidator();
  }

  @Bean
  FeedTomlImportMapper feedTomlImportMapper() {
    return new FeedTomlImportMapper();
  }

  @Bean
  FeedTomlReader feedTomlReader(
      FeedTomlParser parser, FeedTomlSchemaValidator validator, FeedTomlImportMapper mapper) {
    return new FeedTomlReader(parser, validator, mapper);
  }

  @Bean
  FeedTomlWriter feedTomlWriter() {
    return new FeedTomlWriter();
  }

  @Bean
  FeedImportPlanner feedImportPlanner() {
    return new FeedImportPlanner();
  }

  @Bean
  FeedImportExecutor feedImportExecutor(FeedUrlRepository repository) {
    return new FeedImportExecutor(repository);
  }
}
