package net.sasasin.sreader.service.feed;

import java.util.List;
import net.sasasin.sreader.repository.FeedUrlRepository;
import net.sasasin.sreader.service.article.HashIds;
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
      String normalized = FeedUrlNormalizer.normalizeSeedLine(url);
      if (normalized == null) {
        continue;
      }
      if (feedUrlRepository.insertIfAbsent(HashIds.md5(normalized), normalized)) {
        inserted++;
      }
    }
    return inserted;
  }
}
