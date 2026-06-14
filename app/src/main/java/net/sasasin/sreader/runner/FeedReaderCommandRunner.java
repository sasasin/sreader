package net.sasasin.sreader.runner;

import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FeedReaderCommandRunner implements CommandLineRunner {

	private final FeedReaderProperties properties;
	private final FeedReaderScheduler scheduler;
	private final ConfigurableApplicationContext applicationContext;

	public FeedReaderCommandRunner(FeedReaderProperties properties,
			FeedReaderScheduler scheduler,
			ConfigurableApplicationContext applicationContext) {
		this.properties = properties;
		this.scheduler = scheduler;
		this.applicationContext = applicationContext;
	}

	@Override
	public void run(String... args) {
		if (properties.job().runOnce()) {
			scheduler.runIfIdle();
			SpringApplication.exit(applicationContext, () -> 0);
		}
	}
}
