package net.sasasin.sreader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import net.sasasin.sreader.domain.FeedStatus;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.UnsubscribeReason;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public TOML import/export facade. Parsing, policy, and persistence are delegated by
 * responsibility.
 */
@Service
public class FeedTomlService {
  private final FeedUrlRepository feedUrlRepository;
  private final Clock clock;
  private final FeedTomlCodec codec;
  private final FeedImportPlanner planner;
  private final FeedImportExecutor executor;

  @Autowired
  public FeedTomlService(FeedUrlRepository feedUrlRepository) {
    this(
        feedUrlRepository, Clock.systemDefaultZone(), new FeedTomlCodec(), new FeedImportPlanner());
  }

  FeedTomlService(FeedUrlRepository repository, Clock clock) {
    this(repository, clock, new FeedTomlCodec(), new FeedImportPlanner());
  }

  private FeedTomlService(
      FeedUrlRepository repository, Clock clock, FeedTomlCodec codec, FeedImportPlanner planner) {
    this.feedUrlRepository = repository;
    this.clock = clock;
    this.codec = codec;
    this.planner = planner;
    this.executor = new FeedImportExecutor(repository);
  }

  public String exportToml(boolean activeOnly) {
    return codec.exportToml(feedUrlRepository.findAllForExport(activeOnly), clock);
  }

  @Transactional
  public ImportResult importToml(String toml, ImportOptions options) {
    FeedImportExecutor.Counters counters = new FeedImportExecutor.Counters();
    List<String> conflicts = new ArrayList<>();
    for (ImportFeed feed : codec.parse(toml)) {
      FeedUrl current = feedUrlRepository.findByUrl(feed.url()).orElse(null);
      executor.execute(
          planner.plan(current, feed, options.resubscribe()),
          options.dryRun(),
          counters,
          conflicts);
    }
    return new ImportResult(
        counters.inserted,
        counters.updated,
        counters.unchanged,
        counters.unsubscribed,
        counters.resubscribed,
        counters.conflicts,
        List.of(),
        conflicts);
  }

  /** Retained as the existing public parsing entry point. */
  public List<ImportFeed> parse(String toml) {
    return codec.parse(toml);
  }

  public record ImportOptions(boolean dryRun, boolean resubscribe) {}

  public record ImportResult(
      int inserted,
      int updated,
      int unchanged,
      int unsubscribed,
      int resubscribed,
      int conflicts,
      List<String> errors,
      List<String> conflictMessages) {}

  public record ImportFeed(
      int index,
      String url,
      FeedStatus status,
      UnsubscribeReason unsubscribeReason,
      OffsetDateTime unsubscribedAt,
      String note,
      FullTextMethod fullTextMethod) {
    public ImportFeed {
      if (status == null) {
        throw new IllegalArgumentException("status must not be null");
      }
      if (fullTextMethod == null) {
        throw new IllegalArgumentException("fullTextMethod must not be null");
      }
      if (status == FeedStatus.ACTIVE) {
        if (unsubscribeReason != null || unsubscribedAt != null || note != null) {
          throw new IllegalArgumentException(
              "active import feed must not have unsubscribe metadata");
        }
      } else if (status == FeedStatus.UNSUBSCRIBED && unsubscribeReason == null) {
        throw new IllegalArgumentException("unsubscribed import feed requires unsubscribeReason");
      }
    }

    FeedUrl toFeedUrl() {
      return new FeedUrl(
          HashIds.md5(url), url, status, unsubscribeReason, unsubscribedAt, note, fullTextMethod);
    }
  }

  public static class TomlValidationException extends RuntimeException {
    private final List<String> errors;

    TomlValidationException(List<String> errors) {
      super(String.join("; ", errors));
      this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
      return errors;
    }
  }
}
