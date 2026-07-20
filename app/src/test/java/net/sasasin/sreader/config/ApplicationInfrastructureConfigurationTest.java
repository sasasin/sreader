package net.sasasin.sreader.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class ApplicationInfrastructureConfigurationTest {

  @Autowired private ApplicationContext context;
  @Autowired private Clock clock;
  @Autowired private HttpClient httpClient;

  @Test
  void registersSingleClockAndHttpClientBeans() {
    assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
    assertThat(context.getBeansOfType(HttpClient.class)).hasSize(1);
    assertThat(clock.getZone()).isEqualTo(ZoneId.systemDefault());
    assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
  }
}
