package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class PicocliFactoryTest {

  static class NoArgType {}

  static class NoNoArgType {
    NoNoArgType(String value) {}
  }

  @Test
  void returnsContextBeanWhenRegistered() throws Exception {
    ApplicationContext context = mock(ApplicationContext.class);
    NoArgType bean = new NoArgType();
    when(context.getBeanNamesForType(NoArgType.class)).thenReturn(new String[] {"noArgType"});
    when(context.getBean(NoArgType.class)).thenReturn(bean);

    PicocliFactory factory = new PicocliFactory(context);

    assertThat(factory.create(NoArgType.class)).isSameAs(bean);
    verify(context).getBean(NoArgType.class);
  }

  @Test
  void fallsBackToNoArgConstructorWhenContextHasNoBean() throws Exception {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBeanNamesForType(NoArgType.class)).thenReturn(new String[0]);

    PicocliFactory factory = new PicocliFactory(context);

    assertThat(factory.create(NoArgType.class)).isInstanceOf(NoArgType.class);
    verify(context, never()).getBean(NoArgType.class);
  }

  @Test
  void fallsBackToNoArgConstructorWhenContextIsNull() throws Exception {
    PicocliFactory factory = new PicocliFactory(null);

    assertThat(factory.create(NoArgType.class)).isInstanceOf(NoArgType.class);
  }

  @Test
  void propagatesReflectionFailureWhenNoNoArgConstructor() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBeanNamesForType(NoNoArgType.class)).thenReturn(new String[0]);
    PicocliFactory factory = new PicocliFactory(context);

    assertThatThrownBy(() -> factory.create(NoNoArgType.class))
        .isInstanceOf(NoSuchMethodException.class);
  }
}
