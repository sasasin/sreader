package net.sasasin.sreader.cli;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.FullTextProbeService;
import net.sasasin.sreader.service.ProbeOutcome;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(
    name = "feed",
    description = {
      "Fetch a feed, select one entry, extract full text with chosen method, print text.",
      "Entry selection priority: --entry-index > --entry-title-regex > --entry-url-regex > --entry"
          + " latest > --entry first (default first).",
      "For --method feed: uses feed entry content/desc (no article fetch). --xpath invalid with"
          + " feed method.",
      "STDOUT: body text. --verbose: diagnostics to STDERR."
    },
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class ProbeFeedCommand implements Callable<Integer> {

  private final FullTextProbeService fullTextProbeService;

  @Spec private CommandSpec spec;

  @Option(
      names = "--feed-url",
      paramLabel = "<RSS_OR_ATOM_URL>",
      description = "RSS or Atom feed URL to probe",
      required = true)
  private String feedUrl;

  @Option(
      names = "--method",
      paramLabel = "<METHOD>",
      description =
          "Full text method (feed uses entry body; others fetch article URL from chosen entry)",
      required = true,
      converter = FullTextMethodConverter.class)
  private FullTextMethod method;

  @Option(
      names = "--xpath",
      paramLabel = "<XPATH>",
      description = "XPath override (not for feed method)")
  private String xpath;

  @Option(
      names = "--entry",
      paramLabel = "<first|latest>",
      description = "Select first or latest entry (by date)")
  private String entry;

  @Option(
      names = "--entry-index",
      paramLabel = "<N>",
      description = "0-based index of entry in feed")
  private Integer entryIndex;

  @Option(
      names = "--entry-title-regex",
      paramLabel = "<REGEX>",
      description = "Select first entry whose title matches regex")
  private String entryTitleRegex;

  @Option(
      names = "--entry-url-regex",
      paramLabel = "<REGEX>",
      description = "Select first entry whose link matches regex")
  private String entryUrlRegex;

  @Option(names = "--verbose", description = "Print diagnostics to STDERR")
  private boolean verbose;

  @Option(
      names = {"--output", "-o"},
      paramLabel = "<PATH>",
      description = "Write text to file instead of STDOUT")
  private String output;

  @Option(names = "--max-chars", paramLabel = "<N>", description = "Truncate output to N chars")
  private Integer maxChars;

  public ProbeFeedCommand(FullTextProbeService fullTextProbeService) {
    this.fullTextProbeService = fullTextProbeService;
  }

  @Override
  public Integer call() {
    try {
      URI uri = UrlValidator.validateHttpUrl(feedUrl, "--feed-url", spec);

      if (xpath != null && !xpath.isBlank() && method == FullTextMethod.FEED) {
        throw new ParameterException(
            spec.commandLine(), "--xpath cannot be used with --method feed");
      }

      FeedEntrySelection selection = resolveSelection();

      Optional<String> xp =
          (xpath != null && !xpath.isBlank()) ? Optional.of(xpath) : Optional.empty();

      ProbeOutcome outcome = fullTextProbeService.probeFeed(uri, method, selection, xp);
      return mapOutcome(outcome);
    } catch (ParameterException pe) {
      throw pe;
    } catch (RuntimeException e) {
      spec.commandLine().getErr().println("Error: " + e.getMessage());
      return CliExitCodes.EXECUTION_ERROR;
    }
  }

  private int mapOutcome(ProbeOutcome outcome) {
    ProbeOutputWriter writer = new ProbeOutputWriter(spec);
    return switch (outcome) {
      case ProbeOutcome.Succeeded succeeded ->
          writer.writeSucceeded(succeeded.document(), succeeded.text(), verbose, output, maxChars);
      case ProbeOutcome.NoContent noContent -> {
        if (verbose) {
          writer.writeNoContentDiagnostics(noContent.document());
        }
        yield CliExitCodes.EMPTY_RESULT;
      }
      case ProbeOutcome.NoMatchingEntry noMatch -> {
        spec.commandLine().getErr().println("No matching feed entry: " + noMatch.message());
        yield CliExitCodes.NO_MATCHING_ENTRY;
      }
      case ProbeOutcome.Skipped skipped -> {
        spec.commandLine().getErr().println(skipped.message());
        yield CliExitCodes.PLAYWRIGHT_DISABLED;
      }
      case ProbeOutcome.InvalidRequest invalid -> {
        spec.commandLine().getErr().println(invalid.message());
        yield CliExitCodes.USAGE_ERROR;
      }
      case ProbeOutcome.Failed failed -> {
        if (failed.failure().interrupted()) {
          Thread.currentThread().interrupt();
        }
        spec.commandLine().getErr().println("Error: " + failed.failure().message());
        yield CliExitCodes.EXECUTION_ERROR;
      }
    };
  }

  private FeedEntrySelection resolveSelection() {
    if (entryIndex != null) {
      try {
        return FeedEntrySelection.index(entryIndex);
      } catch (IllegalArgumentException e) {
        throw new ParameterException(
            spec.commandLine(), "Invalid --entry-index value: " + e.getMessage());
      }
    }
    if (entryTitleRegex != null && !entryTitleRegex.isBlank()) {
      try {
        return FeedEntrySelection.titleRegex(entryTitleRegex);
      } catch (IllegalArgumentException e) {
        throw new ParameterException(
            spec.commandLine(), "Invalid --entry-title-regex value: " + e.getMessage());
      }
    }
    if (entryUrlRegex != null && !entryUrlRegex.isBlank()) {
      try {
        return FeedEntrySelection.urlRegex(entryUrlRegex);
      } catch (IllegalArgumentException e) {
        throw new ParameterException(
            spec.commandLine(), "Invalid --entry-url-regex value: " + e.getMessage());
      }
    }
    if (entry != null) {
      String e = entry.trim().toLowerCase();
      if ("latest".equals(e)) {
        return FeedEntrySelection.latest();
      }
      if ("first".equals(e)) {
        return FeedEntrySelection.first();
      }
      throw new ParameterException(
          spec.commandLine(), "Invalid --entry value, must be first or latest: " + entry);
    }
    return FeedEntrySelection.first();
  }
}
