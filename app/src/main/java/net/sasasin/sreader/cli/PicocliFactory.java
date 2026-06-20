package net.sasasin.sreader.cli;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine.IFactory;

/**
 * IFactory implementation backed by Spring ApplicationContext so that picocli command classes
 * annotated as @Component can receive dependency injection.
 */
@Component
public class PicocliFactory implements IFactory {

  private final ApplicationContext applicationContext;

  public PicocliFactory(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public <K> K create(Class<K> cls) throws Exception {
    if (applicationContext != null && applicationContext.getBeanNamesForType(cls).length > 0) {
      return applicationContext.getBean(cls);
    }
    // Fallback for non-Spring managed classes (should not be needed for our commands)
    return cls.getDeclaredConstructor().newInstance();
  }
}
