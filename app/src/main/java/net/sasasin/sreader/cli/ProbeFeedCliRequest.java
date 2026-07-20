package net.sasasin.sreader.cli;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/**
 * Validated immutable CLI boundary for {@code probe feed}. Invalid method/xpath combinations and
 * missing selection cannot be constructed.
 */
final class ProbeFeedCliRequest {

  private final URI feedUrl;
  private final FullTextMethod method;
  private final FeedEntrySelection selection;
  private final Optional<String> xpath;
  private final boolean verbose;
  private final String output;
  private final Integer maxChars;

  private ProbeFeedCliRequest(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpath,
      boolean verbose,
      String output,
      Integer maxChars) {
    this.feedUrl = Objects.requireNonNull(feedUrl, "feedUrl");
    this.method = Objects.requireNonNull(method, "method");
    this.selection = Objects.requireNonNull(selection, "selection");
    this.xpath = Objects.requireNonNull(xpath, "xpath");
    this.verbose = verbose;
    this.output = output;
    this.maxChars = maxChars;
  }

  static ProbeFeedCliRequest create(
      CommandSpec spec,
      String feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      String xpath,
      boolean verbose,
      String output,
      Integer maxChars) {
    Objects.requireNonNull(spec, "spec");
    if (method == null) {
      throw new ParameterException(spec.commandLine(), "--method is required");
    }
    if (selection == null) {
      throw new ParameterException(spec.commandLine(), "entry selection is required");
    }
    URI validatedUrl = UrlValidator.validateHttpUrl(feedUrl, "--feed-url", spec);
    Optional<String> normalizedXpath = normalizeXpath(xpath);
    if (normalizedXpath.isPresent() && !method.supportsXpathOverride()) {
      throw new ParameterException(spec.commandLine(), "--xpath cannot be used with --method feed");
    }
    return new ProbeFeedCliRequest(
        validatedUrl, method, selection, normalizedXpath, verbose, output, maxChars);
  }

  URI feedUrl() {
    return feedUrl;
  }

  FullTextMethod method() {
    return method;
  }

  FeedEntrySelection selection() {
    return selection;
  }

  Optional<String> xpath() {
    return xpath;
  }

  boolean verbose() {
    return verbose;
  }

  String output() {
    return output;
  }

  Integer maxChars() {
    return maxChars;
  }

  private static Optional<String> normalizeXpath(String xpath) {
    if (xpath == null || xpath.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(xpath);
  }
}
