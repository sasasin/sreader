package net.sasasin.sreader.service.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class HttpDependencyWiringTest {

  @Autowired private ApplicationContext context;
  @Autowired private HttpFetchService service;
  @Autowired private HttpClient client;
  @Autowired private FeedReaderProperties properties;

  @Test
  void serviceUsesManagedHttpClientBean() {
    assertThat(context.getBeansOfType(HttpClient.class)).hasSize(1);
    assertThat(context.getBeansOfType(HttpFetchService.class)).hasSize(1);
    assertThat(ReflectionTestUtils.getField(service, "client")).isSameAs(client);
    assertThat(ReflectionTestUtils.getField(service, "properties")).isSameAs(properties);
  }

  @Test
  void managedClientUsesConfiguredConnectTimeoutAndNormalRedirects() {
    assertThat(client.connectTimeout()).contains(properties.http().connectTimeout());
    assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
    assertThat(properties.http().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
  }
}
