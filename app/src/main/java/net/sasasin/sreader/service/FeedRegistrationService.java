package net.sasasin.sreader.service;

import java.util.List;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.springframework.stereotype.Service;

@Service
public class FeedRegistrationService {

	private final FeedUrlRepository feedUrlRepository;

	public FeedRegistrationService(FeedUrlRepository feedUrlRepository) {
		this.feedUrlRepository = feedUrlRepository;
	}

	public int registerFeedUrls(List<String> urls) {
		int inserted = 0;
		for (String url : urls) {
			String normalized = normalize(url);
			if (normalized == null) {
				continue;
			}
			if (feedUrlRepository.insertIfAbsent(HashIds.md5(normalized), normalized)) {
				inserted++;
			}
		}
		return inserted;
	}

	private String normalize(String url) {
		if (url == null) {
			return null;
		}
		String trimmed = url.trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return null;
		}
		return trimmed.split("\\t")[0];
	}
}
