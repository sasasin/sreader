package net.sasasin.sreader;

import net.sasasin.sreader.config.FeedReaderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(FeedReaderProperties.class)
public class SreaderApplication {

  public static void main(String[] args) {
    SpringApplication.run(SreaderApplication.class, args);
  }
}
