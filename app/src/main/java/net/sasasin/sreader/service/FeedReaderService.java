package net.sasasin.sreader.service;

import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeedReaderService {

	private static final Logger logger = LoggerFactory.getLogger(FeedReaderService.class);

	private final FeedReaderProperties properties;
	private final FeedRegistrationService feedRegistrationService;
	private final FeedUrlRepository feedUrlRepository;
	private final FeedEntryImportService feedEntryImportService;
	private final FullTextExtractionService fullTextExtractionService;

	public FeedReaderService(FeedReaderProperties properties,
			FeedRegistrationService feedRegistrationService,
			FeedUrlRepository feedUrlRepository,
			FeedEntryImportService feedEntryImportService,
			FullTextExtractionService fullTextExtractionService) {
		this.properties = properties;
		this.feedRegistrationService = feedRegistrationService;
		this.feedUrlRepository = feedUrlRepository;
		this.feedEntryImportService = feedEntryImportService;
		this.fullTextExtractionService = fullTextExtractionService;
	}

	public JobResult runOnce() {
		int seeded = feedRegistrationService.registerFeedUrls(properties.seedFeedUrls());
		int headers = 0;
		for (FeedUrl feedUrl : feedUrlRepository.findActiveForReading()) {
			headers += feedEntryImportService.importEntries(feedUrl);
		}
		int fullTexts = fullTextExtractionService.extractPending(100);
		JobResult result = new JobResult(seeded, headers, fullTexts);
		logger.info("Feed reader job finished: {}", result);
		return result;
	}

	public record JobResult(int feedUrlsInserted, int contentHeadersInserted, int fullTextsInserted) {
	}
}
