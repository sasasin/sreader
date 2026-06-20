package net.sasasin.sreader.cli;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.FeedDiscoveryService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "discover",
    description = {
      "Discover RSS/Atom (and similar) feed URLs from a web site page using Playwright.",
      "Outputs one URL per line (urls format) or TOML fragment (toml format).",
      "Default --format urls, --method http (used only for the generated toml full_text_method)."
    },
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class FeedsDiscoverCommand implements Callable<Integer> {

  private final FeedDiscoveryService feedDiscoveryService;

  @Spec private CommandSpec spec;

  @Option(
      names = "--site-url",
      paramLabel = "<WEB_SITE_URL>",
      description = "Site URL to scan for feeds (http/https)",
      required = true)
  private String siteUrl;

  @Option(
      names = "--format",
      paramLabel = "<urls|toml>",
      description = "Output format",
      defaultValue = "urls")
  private String format;

  @Option(
      names = "--method",
      paramLabel = "<METHOD>",
      description = "full_text_method value to embed when --format toml",
      defaultValue = "http",
      converter = FullTextMethodConverter.class)
  private FullTextMethod method;

  @Option(names = "--verbose", description = "Print discovery diagnostics to STDERR")
  private boolean verbose;

  public FeedsDiscoverCommand(FeedDiscoveryService feedDiscoveryService) {
    this.feedDiscoveryService = feedDiscoveryService;
  }

  @Override
  public Integer call() {
    try {
      URI uri = UrlValidator.validateHttpUrl(siteUrl, "--site-url", spec);

      List<URI> discovered = feedDiscoveryService.discover(uri);

      if (verbose) {
        spec.commandLine().getErr().printf("siteUrl=%s%n", uri);
        spec.commandLine().getErr().printf("discovered=%d%n", discovered.size());
      }

      String fmt = (format != null ? format : "urls").trim().toLowerCase();
      if ("toml".equals(fmt)) {
        String m = method != null ? method.value() : "http";
        for (URI u : discovered) {
          spec.commandLine().getOut().println("[[feeds]]");
          spec.commandLine().getOut().println("url = \"" + escape(u.toString()) + "\"");
          spec.commandLine().getOut().println("status = \"active\"");
          spec.commandLine().getOut().println("full_text_method = \"" + m + "\"");
          spec.commandLine().getOut().println();
        }
      } else {
        // urls
        for (URI u : discovered) {
          spec.commandLine().getOut().println(u);
        }
      }
      return 0;
    } catch (picocli.CommandLine.ParameterException pe) {
      throw pe;
    } catch (Exception e) {
      spec.commandLine().getErr().println("Error: " + e.getMessage());
      return 1;
    }
  }

  private String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
