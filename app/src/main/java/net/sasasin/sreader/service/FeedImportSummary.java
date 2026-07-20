package net.sasasin.sreader.service;

/**
 * Per-feed or accumulated counters for entry import outcomes. Counts are non-negative; use {@link
 * #plus(FeedImportSummary)} to accumulate without positional noise.
 */
public record FeedImportSummary(
    int entriesSeen,
    int insertedHeaders,
    int existingHeaders,
    int missingLinks,
    int redirectFallbacks,
    int feedTextsInserted,
    int feedTextsAlreadyPresent,
    int feedTextsWithoutContent) {

  public FeedImportSummary {
    entriesSeen = OutcomePreconditions.requireNonNegative("entriesSeen", entriesSeen);
    insertedHeaders = OutcomePreconditions.requireNonNegative("insertedHeaders", insertedHeaders);
    existingHeaders = OutcomePreconditions.requireNonNegative("existingHeaders", existingHeaders);
    missingLinks = OutcomePreconditions.requireNonNegative("missingLinks", missingLinks);
    redirectFallbacks =
        OutcomePreconditions.requireNonNegative("redirectFallbacks", redirectFallbacks);
    feedTextsInserted =
        OutcomePreconditions.requireNonNegative("feedTextsInserted", feedTextsInserted);
    feedTextsAlreadyPresent =
        OutcomePreconditions.requireNonNegative("feedTextsAlreadyPresent", feedTextsAlreadyPresent);
    feedTextsWithoutContent =
        OutcomePreconditions.requireNonNegative("feedTextsWithoutContent", feedTextsWithoutContent);
  }

  public static FeedImportSummary empty() {
    return new FeedImportSummary(0, 0, 0, 0, 0, 0, 0, 0);
  }

  public FeedImportSummary plus(FeedImportSummary other) {
    return new FeedImportSummary(
        entriesSeen + other.entriesSeen,
        insertedHeaders + other.insertedHeaders,
        existingHeaders + other.existingHeaders,
        missingLinks + other.missingLinks,
        redirectFallbacks + other.redirectFallbacks,
        feedTextsInserted + other.feedTextsInserted,
        feedTextsAlreadyPresent + other.feedTextsAlreadyPresent,
        feedTextsWithoutContent + other.feedTextsWithoutContent);
  }

  FeedImportSummary plusEntry(FeedEntryImportOutcome outcome) {
    FeedImportSummary base =
        new FeedImportSummary(
            entriesSeen + 1,
            insertedHeaders,
            existingHeaders,
            missingLinks,
            redirectFallbacks,
            feedTextsInserted,
            feedTextsAlreadyPresent,
            feedTextsWithoutContent);
    return switch (outcome) {
      case FeedEntryImportOutcome.Inserted inserted -> base.withInserted(inserted);
      case FeedEntryImportOutcome.AlreadyPresent already -> base.withExisting(already.redirect());
      case FeedEntryImportOutcome.MissingLink ignored ->
          new FeedImportSummary(
              base.entriesSeen,
              base.insertedHeaders,
              base.existingHeaders,
              base.missingLinks + 1,
              base.redirectFallbacks,
              base.feedTextsInserted,
              base.feedTextsAlreadyPresent,
              base.feedTextsWithoutContent);
      case FeedEntryImportOutcome.Failed ignored -> base;
    };
  }

  private FeedImportSummary withInserted(FeedEntryImportOutcome.Inserted inserted) {
    int redirects = redirectFallbacks + (isFallback(inserted.redirect()) ? 1 : 0);
    int textsInserted = feedTextsInserted;
    int textsExisting = feedTextsAlreadyPresent;
    int textsNone = feedTextsWithoutContent;
    if (inserted.feedTextWrite().isPresent()) {
      switch (inserted.feedTextWrite().get()) {
        case INSERTED -> textsInserted++;
        case ALREADY_EXISTS -> textsExisting++;
        case NO_CONTENT -> textsNone++;
      }
    }
    return new FeedImportSummary(
        entriesSeen,
        insertedHeaders + 1,
        existingHeaders,
        missingLinks,
        redirects,
        textsInserted,
        textsExisting,
        textsNone);
  }

  private FeedImportSummary withExisting(RedirectResolution redirect) {
    return new FeedImportSummary(
        entriesSeen,
        insertedHeaders,
        existingHeaders + 1,
        missingLinks,
        redirectFallbacks + (isFallback(redirect) ? 1 : 0),
        feedTextsInserted,
        feedTextsAlreadyPresent,
        feedTextsWithoutContent);
  }

  private static boolean isFallback(RedirectResolution redirect) {
    return redirect instanceof RedirectResolution.Fallback;
  }
}
