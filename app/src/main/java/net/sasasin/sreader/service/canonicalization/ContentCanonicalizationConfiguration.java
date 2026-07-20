package net.sasasin.sreader.service.canonicalization;

import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;
import net.sasasin.sreader.service.article.ArticleUrlCanonicalizer;
import net.sasasin.sreader.service.text.ContentTextFileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition root for content canonicalization maintenance collaborators. */
@Configuration(proxyBeanMethods = false)
class ContentCanonicalizationConfiguration {

  @Bean
  ContentCanonicalizationCandidateScanner contentCanonicalizationCandidateScanner(
      ArticleUrlCanonicalizer canonicalizer,
      ContentCanonicalizationMaintenanceRepository repository) {
    return new ContentCanonicalizationCandidateScanner(canonicalizer, repository);
  }

  @Bean
  ContentCanonicalizationPlanner contentCanonicalizationPlanner() {
    return new ContentCanonicalizationPlanner();
  }

  @Bean
  ContentCanonicalizationExecutor contentCanonicalizationExecutor(
      ContentCanonicalizationMaintenanceRepository repository) {
    return new ContentCanonicalizationExecutor(repository);
  }

  @Bean
  ContentCanonicalizationFileCleaner contentCanonicalizationFileCleaner(
      ContentTextFileStore fileStore) {
    return new ContentCanonicalizationFileCleaner(fileStore);
  }
}
