package net.sasasin.sreader.cli;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/**
 * Validated immutable CLI boundary for {@code probe feed}. Invalid method/xpath/dataset
 * combinations and missing selection cannot be constructed.
 */
final class ProbeFeedCliRequest {

  private final URI feedUrl;
  private final FullTextMethod method;
  private final FeedEntrySelection selection;
  private final Optional<String> xpath;
  private final boolean verbose;
  private final Optional<String> output;
  private final Optional<Integer> maxChars;
  private final Optional<Long> autopagerizeDatasetId;

  private ProbeFeedCliRequest(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpath,
      boolean verbose,
      Optional<String> output,
      Optional<Integer> maxChars,
      Optional<Long> autopagerizeDatasetId) {
    this.feedUrl = Objects.requireNonNull(feedUrl, "feedUrl");
    this.method = Objects.requireNonNull(method, "method");
    this.selection = Objects.requireNonNull(selection, "selection");
    this.xpath = Objects.requireNonNull(xpath, "xpath");
    this.verbose = verbose;
    this.output = Objects.requireNonNull(output, "output");
    this.maxChars = Objects.requireNonNull(maxChars, "maxChars");
    this.autopagerizeDatasetId =
        Objects.requireNonNull(autopagerizeDatasetId, "autopagerizeDatasetId");
  }

  static ProbeFeedCliRequest create(
      CommandSpec spec,
      String feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      String xpath,
      boolean verbose,
      String output,
      Integer maxChars,
      Long autopagerizeDatasetId) {
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
    Optional<Long> datasetId = Optional.ofNullable(autopagerizeDatasetId);
    if (datasetId.isPresent()) {
      if (datasetId.get() < 1) {
        throw new ParameterException(
            spec.commandLine(), "--autopagerize-dataset-id must be a positive integer");
      }
      if (!method.usesAutopagerize()) {
        throw new ParameterException(
            spec.commandLine(),
            "--autopagerize-dataset-id is only valid with AutoPagerize methods");
      }
    }
    return new ProbeFeedCliRequest(
        validatedUrl,
        method,
        selection,
        normalizedXpath,
        verbose,
        Optional.ofNullable(output),
        Optional.ofNullable(maxChars),
        datasetId);
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

  Optional<String> output() {
    return output;
  }

  Optional<Integer> maxChars() {
    return maxChars;
  }

  Optional<Long> autopagerizeDatasetId() {
    return autopagerizeDatasetId;
  }

  private static Optional<String> normalizeXpath(String xpath) {
    if (xpath == null || xpath.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(xpath);
  }
}
