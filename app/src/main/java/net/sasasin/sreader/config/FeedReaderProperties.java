package net.sasasin.sreader.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sreader")
public record FeedReaderProperties(
    Scheduler scheduler, Job job, Http http, List<String> seedFeedUrls) {

  public FeedReaderProperties {
    if (scheduler == null) {
      scheduler = new Scheduler(true, "0 */15 * * * *");
    }
    if (job == null) {
      job = new Job(false);
    }
    if (http == null) {
      http = new Http("SReader/0.1", Duration.ofSeconds(5), Duration.ofSeconds(20), 1);
    }
    if (seedFeedUrls == null) {
      seedFeedUrls = List.of();
    }
  }

  public record Scheduler(boolean enabled, String cron) {}

  public record Job(boolean runOnce) {}

  public record Http(
      String userAgent, Duration connectTimeout, Duration readTimeout, int retryCount) {}
}
