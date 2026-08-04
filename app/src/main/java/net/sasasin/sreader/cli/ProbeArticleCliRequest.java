package net.sasasin.sreader.cli;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.FullTextMethod;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/**
 * Validated immutable CLI boundary for {@code probe article}. Invalid method/xpath/dataset
 * combinations cannot be constructed.
 */
final class ProbeArticleCliRequest {

  private final URI url;
  private final FullTextMethod method;
  private final Optional<String> xpath;
  private final boolean verbose;
  private final Optional<String> output;
  private final Optional<Integer> maxChars;
  private final Optional<Long> autopagerizeDatasetId;

  private ProbeArticleCliRequest(
      URI url,
      FullTextMethod method,
      Optional<String> xpath,
      boolean verbose,
      Optional<String> output,
      Optional<Integer> maxChars,
      Optional<Long> autopagerizeDatasetId) {
    this.url = Objects.requireNonNull(url, "url");
    this.method = Objects.requireNonNull(method, "method");
    this.xpath = Objects.requireNonNull(xpath, "xpath");
    this.verbose = verbose;
    this.output = Objects.requireNonNull(output, "output");
    this.maxChars = Objects.requireNonNull(maxChars, "maxChars");
    this.autopagerizeDatasetId =
        Objects.requireNonNull(autopagerizeDatasetId, "autopagerizeDatasetId");
  }

  static ProbeArticleCliRequest create(
      CommandSpec spec,
      String url,
      FullTextMethod method,
      String xpath,
      boolean verbose,
      String output,
      Integer maxChars,
      Long autopagerizeDatasetId) {
    Objects.requireNonNull(spec, "spec");
    if (method == null) {
      throw new ParameterException(spec.commandLine(), "--method is required");
    }
    if (!method.supportsArticleProbe()) {
      throw new ParameterException(
          spec.commandLine(), "--method feed is not valid for 'probe article'");
    }
    URI validatedUrl = UrlValidator.validateHttpUrl(url, "--url", spec);
    Optional<String> normalizedXpath = normalizeXpath(xpath);
    if (normalizedXpath.isPresent() && !method.supportsXpathOverride()) {
      throw new ParameterException(
          spec.commandLine(), "--xpath cannot be used with --method " + method.value());
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
    return new ProbeArticleCliRequest(
        validatedUrl,
        method,
        normalizedXpath,
        verbose,
        Optional.ofNullable(output),
        Optional.ofNullable(maxChars),
        datasetId);
  }

  URI url() {
    return url;
  }

  FullTextMethod method() {
    return method;
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
