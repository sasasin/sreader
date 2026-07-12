package net.sasasin.sreader.cli;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.ProbeResult;
import net.sasasin.sreader.service.FullTextProbeService;
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
      description =
          "Full text method: feed|http|http_readability|playwright|playwright_readability"
              + "|playwright_infy_scroll|playwright_infy_scroll_readability",
      required = true,
      converter = FullTextMethodConverter.class)
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
      URI uri = UrlValidator.validateHttpUrl(url, "--url", spec);

      if (method == FullTextMethod.FEED) {
        // usage error via picocli style
        throw new picocli.CommandLine.ParameterException(
            spec.commandLine(), "--method feed is not valid for 'probe article'");
      }

      Optional<String> xp =
          (xpath != null && !xpath.isBlank()) ? Optional.of(xpath) : Optional.empty();

      ProbeResult result = fullTextProbeService.probeArticle(uri, method, xp);

      ProbeOutputWriter writer = new ProbeOutputWriter(spec);
      if (result.text() == null || result.text().isBlank()) {
        if (verbose) {
          // still emit diagnostics
          writer.writeResult(result, true, output, maxChars);
        }
        return 4;
      }

      return writer.writeResult(result, verbose, output, maxChars);
    } catch (picocli.CommandLine.ParameterException pe) {
      throw pe; // let picocli handle as usage 2
    } catch (FullTextProbeService.PlaywrightDisabledException e) {
      spec.commandLine().getErr().println(e.getMessage());
      return 5;
    } catch (Exception e) {
      spec.commandLine().getErr().println("Error: " + e.getMessage());
      return 1;
    }
  }
}
