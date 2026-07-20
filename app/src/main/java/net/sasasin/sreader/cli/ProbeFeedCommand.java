package net.sasasin.sreader.cli;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import net.sasasin.sreader.service.probe.ProbeOutcome;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "feed",
    description = {
      "Fetch a feed, select one entry, extract full text with chosen method, print text.",
      "Choose at most one entry selector; default is first entry.",
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
          "Full text method (feed uses entry body; others fetch article URL from chosen entry)."
              + " Invalid input reports all valid values.",
      required = true,
      converter = FullTextMethodConverter.class,
      completionCandidates = FullTextMethodCandidates.class)
  private FullTextMethod method;

  @Option(
      names = "--xpath",
      paramLabel = "<XPATH>",
      description = "XPath override (not for feed method)")
  private String xpath;

  @ArgGroup(
      exclusive = true,
      multiplicity = "0..1",
      heading = "Entry selection (choose at most one; default: first):%n")
  private EntrySelectionOptions entrySelection;

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
      ProbeFeedCliRequest request =
          ProbeFeedCliRequest.create(
              spec, feedUrl, method, selection(), xpath, verbose, output, maxChars);

      ProbeOutcome outcome =
          fullTextProbeService.probeFeed(
              request.feedUrl(), request.method(), request.selection(), request.xpath());
      return mapOutcome(outcome, request);
    } catch (picocli.CommandLine.ParameterException pe) {
      throw pe;
    } catch (RuntimeException e) {
      spec.commandLine().getErr().println("Error: " + e.getMessage());
      return CliExitCodes.EXECUTION_ERROR;
    }
  }

  private FeedEntrySelection selection() {
    return entrySelection == null ? FeedEntrySelection.first() : entrySelection.selection();
  }

  private int mapOutcome(ProbeOutcome outcome, ProbeFeedCliRequest request) {
    ProbeOutputWriter writer = new ProbeOutputWriter(spec);
    return switch (outcome) {
      case ProbeOutcome.Succeeded succeeded ->
          writer.writeSucceeded(
              succeeded.document(),
              succeeded.text(),
              request.verbose(),
              request.output(),
              request.maxChars());
      case ProbeOutcome.NoContent noContent -> {
        if (request.verbose()) {
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

  /**
   * Optional exclusive group of entry selectors. When the group is omitted, {@link
   * FeedEntrySelection#first()} is used by the command.
   */
  static final class EntrySelectionOptions {

    @Option(
        names = "--entry",
        converter = EntryPositionSelectionConverter.class,
        paramLabel = "<first|latest>",
        description = "Select first or latest entry (by date)")
    FeedEntrySelection position;

    @Option(
        names = "--entry-index",
        converter = EntryIndexSelectionConverter.class,
        paramLabel = "<N>",
        description = "0-based index of entry in feed")
    FeedEntrySelection index;

    @Option(
        names = "--entry-title-regex",
        converter = EntryTitleRegexSelectionConverter.class,
        paramLabel = "<REGEX>",
        description = "Select first entry whose title matches regex")
    FeedEntrySelection titleRegex;

    @Option(
        names = "--entry-url-regex",
        converter = EntryUrlRegexSelectionConverter.class,
        paramLabel = "<REGEX>",
        description = "Select first entry whose link matches regex")
    FeedEntrySelection urlRegex;

    FeedEntrySelection selection() {
      List<FeedEntrySelection> specified =
          Stream.of(position, index, titleRegex, urlRegex).filter(Objects::nonNull).toList();
      if (specified.size() != 1) {
        throw new IllegalStateException("Picocli entry selector group invariant violated");
      }
      return specified.getFirst();
    }
  }
}
