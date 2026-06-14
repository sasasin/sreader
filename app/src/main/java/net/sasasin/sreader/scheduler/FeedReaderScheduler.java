package net.sasasin.sreader.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.FeedReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeedReaderScheduler {

	private static final Logger logger = LoggerFactory.getLogger(FeedReaderScheduler.class);

	private final FeedReaderProperties properties;
	private final FeedReaderService feedReaderService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public FeedReaderScheduler(FeedReaderProperties properties, FeedReaderService feedReaderService) {
		this.properties = properties;
		this.feedReaderService = feedReaderService;
	}

	@Scheduled(cron = "${sreader.scheduler.cron}")
	public void runScheduled() {
		if (!properties.scheduler().enabled()) {
			return;
		}
		runIfIdle();
	}

	public boolean runIfIdle() {
		if (!running.compareAndSet(false, true)) {
			logger.info("Feed reader job is already running; skip this trigger");
			return false;
		}
		try {
			feedReaderService.runOnce();
			return true;
		} finally {
			running.set(false);
		}
	}
}
