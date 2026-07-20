package net.sasasin.sreader.cli;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.FullTextMethod;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/**
 * Validated immutable CLI boundary for {@code probe article}. Invalid method/xpath combinations
 * cannot be constructed.
 */
final class ProbeArticleCliRequest {

  private final URI url;
  private final FullTextMethod method;
  private final Optional<String> xpath;
  private final boolean verbose;
  private final String output;
  private final Integer maxChars;

  private ProbeArticleCliRequest(
      URI url,
      FullTextMethod method,
      Optional<String> xpath,
      boolean verbose,
      String output,
      Integer maxChars) {
    this.url = Objects.requireNonNull(url, "url");
    this.method = Objects.requireNonNull(method, "method");
    this.xpath = Objects.requireNonNull(xpath, "xpath");
    this.verbose = verbose;
    this.output = output;
    this.maxChars = maxChars;
  }

  static ProbeArticleCliRequest create(
      CommandSpec spec,
      String url,
      FullTextMethod method,
      String xpath,
      boolean verbose,
      String output,
      Integer maxChars) {
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
    return new ProbeArticleCliRequest(
        validatedUrl, method, normalizedXpath, verbose, output, maxChars);
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
