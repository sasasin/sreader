package net.sasasin.sreader.service.feed.toml;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class FeedTomlDependencyWiringTest {

  @Autowired private ApplicationContext context;
  @Autowired private FeedTomlService service;
  @Autowired private FeedTomlReader reader;
  @Autowired private FeedTomlWriter writer;
  @Autowired private FeedImportPlanner planner;
  @Autowired private FeedImportExecutor executor;
  @Autowired private FeedTomlParser parser;
  @Autowired private FeedTomlSchemaValidator validator;
  @Autowired private FeedTomlImportMapper mapper;
  @Autowired private Clock clock;
  @Autowired private FeedUrlRepository repository;

  @Test
  void collaboratorsAreSingleManagedBeansSharedWithService() {
    assertThat(context.getBeansOfType(FeedTomlParser.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedTomlSchemaValidator.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedTomlImportMapper.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedTomlReader.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedTomlWriter.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedImportPlanner.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedImportExecutor.class)).hasSize(1);
    assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
    assertThat(context.getBeansOfType(FeedTomlService.class)).hasSize(1);

    assertThat(ReflectionTestUtils.getField(reader, "parser")).isSameAs(parser);
    assertThat(ReflectionTestUtils.getField(reader, "validator")).isSameAs(validator);
    assertThat(ReflectionTestUtils.getField(reader, "mapper")).isSameAs(mapper);

    Object target = AopTestUtils.getUltimateTargetObject(service);
    assertThat(ReflectionTestUtils.getField(target, "reader")).isSameAs(reader);
    assertThat(ReflectionTestUtils.getField(target, "writer")).isSameAs(writer);
    assertThat(ReflectionTestUtils.getField(target, "planner")).isSameAs(planner);
    assertThat(ReflectionTestUtils.getField(target, "executor")).isSameAs(executor);
    assertThat(ReflectionTestUtils.getField(target, "clock")).isSameAs(clock);
    assertThat(ReflectionTestUtils.getField(executor, "repository")).isSameAs(repository);
  }

  @Test
  void importTomlIsTransactionProxied() {
    assertThat(AopUtils.isAopProxy(service)).isTrue();
  }
}
