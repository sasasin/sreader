package net.sasasin.sreader.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Infrastructure beans shared across features (clock, HTTP client). */
@Configuration(proxyBeanMethods = false)
class ApplicationInfrastructureConfiguration {

  @Bean
  Clock applicationClock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  HttpClient feedReaderHttpClient(FeedReaderProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.http().connectTimeout())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }
}
