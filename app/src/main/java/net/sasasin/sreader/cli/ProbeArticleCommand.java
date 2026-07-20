package net.sasasin.sreader.cli;

import java.util.concurrent.Callable;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.probe.FullTextProbeService;
import net.sasasin.sreader.service.probe.ProbeOutcome;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "article",
    description = {
      "Fetch a single article URL with the specified extraction method and print extracted text.",
      "STDOUT gets the body text only (unless --output). STDERR gets diagnostics only with"
          + " --verbose.",
      "Use --xpath to override extraction to a specific XPath (disables rule lookup and"
          + " readability).",
    },
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class ProbeArticleCommand implements Callable<Integer> {

  private final FullTextProbeService fullTextProbeService;

  @Spec private CommandSpec spec;

  @Option(
      names = "--url",
      paramLabel = "<ARTICLE_URL>",
      description = "Article URL to probe (http/https only, no userinfo)",
      required = true)
  private String url;

  @Option(
      names = "--method",
      paramLabel = "<METHOD>",
      description = "Full text extraction method. Invalid input reports all valid values.",
      required = true,
      converter = FullTextMethodConverter.class,
      completionCandidates = FullTextMethodCandidates.class)
  private FullTextMethod method;

  @Option(
      names = "--xpath",
      paramLabel = "<XPATH>",
      description = "XPath to use instead of DB rules or readability (for testing a rule)")
  private String xpath;

  @Option(
      names = "--verbose",
      description = "Print diagnostic info (input/final URL, title, lengths) to STDERR")
  private boolean verbose;

  @Option(
      names = {"--output", "-o"},
      paramLabel = "<PATH>",
      description = "Write extracted text to this file instead of STDOUT")
  private String output;

  @Option(
      names = "--max-chars",
      paramLabel = "<N>",
      description = "Truncate output text to first N characters (extraction is still full)")
  private Integer maxChars;

  public ProbeArticleCommand(FullTextProbeService fullTextProbeService) {
    this.fullTextProbeService = fullTextProbeService;
  }

  @Override
  public Integer call() {
    try {
      ProbeArticleCliRequest request =
          ProbeArticleCliRequest.create(spec, url, method, xpath, verbose, output, maxChars);

      ProbeOutcome outcome =
          fullTextProbeService.probeArticle(request.url(), request.method(), request.xpath());
      return mapOutcome(outcome, request);
    } catch (picocli.CommandLine.ParameterException pe) {
      throw pe;
    } catch (RuntimeException e) {
      spec.commandLine().getErr().println("Error: " + e.getMessage());
      return CliExitCodes.EXECUTION_ERROR;
    }
  }

  private int mapOutcome(ProbeOutcome outcome, ProbeArticleCliRequest request) {
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
}
