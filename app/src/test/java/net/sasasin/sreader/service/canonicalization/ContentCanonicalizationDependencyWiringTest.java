package net.sasasin.sreader.service.canonicalization;

import static org.assertj.core.api.Assertions.assertThat;

import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;
import net.sasasin.sreader.service.text.ContentTextFileStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ContentCanonicalizationDependencyWiringTest {

  @Autowired private ApplicationContext context;
  @Autowired private ContentCanonicalizationMaintenanceService service;
  @Autowired private ContentCanonicalizationCandidateScanner scanner;
  @Autowired private ContentCanonicalizationPlanner planner;
  @Autowired private ContentCanonicalizationExecutor executor;
  @Autowired private ContentCanonicalizationFileCleaner fileCleaner;
  @Autowired private ContentCanonicalizationMaintenanceRepository repository;
  @Autowired private ContentTextFileStore fileStore;

  @Test
  void collaboratorsAreSingleManagedBeansSharedWithService() {
    assertThat(context.getBeansOfType(ContentCanonicalizationCandidateScanner.class)).hasSize(1);
    assertThat(context.getBeansOfType(ContentCanonicalizationPlanner.class)).hasSize(1);
    assertThat(context.getBeansOfType(ContentCanonicalizationExecutor.class)).hasSize(1);
    assertThat(context.getBeansOfType(ContentCanonicalizationFileCleaner.class)).hasSize(1);
    assertThat(context.getBeansOfType(ContentCanonicalizationMaintenanceService.class)).hasSize(1);

    assertThat(ReflectionTestUtils.getField(service, "candidateScanner")).isSameAs(scanner);
    assertThat(ReflectionTestUtils.getField(service, "planner")).isSameAs(planner);
    assertThat(ReflectionTestUtils.getField(service, "executor")).isSameAs(executor);
    assertThat(ReflectionTestUtils.getField(service, "fileCleaner")).isSameAs(fileCleaner);
    assertThat(ReflectionTestUtils.getField(scanner, "repository")).isSameAs(repository);
    assertThat(ReflectionTestUtils.getField(executor, "repository")).isSameAs(repository);
    assertThat(ReflectionTestUtils.getField(fileCleaner, "fileStore")).isSameAs(fileStore);
  }
}
