package net.sasasin.sreader.cli;

import java.net.URI;
import java.net.URISyntaxException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

public final class UrlValidator {

  private UrlValidator() {}

  public static URI validateHttpUrl(String urlStr, String optionName, CommandSpec spec) {
    if (urlStr == null || urlStr.isBlank()) {
      throw new ParameterException(spec.commandLine(), optionName + " must not be blank");
    }
    URI uri;
    try {
      uri = new URI(urlStr);
    } catch (URISyntaxException e) {
      throw new ParameterException(
          spec.commandLine(), "Invalid URL for " + optionName + ": " + urlStr, e);
    }
    if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
      throw new ParameterException(
          spec.commandLine(), optionName + " must use http or https scheme: " + urlStr);
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ParameterException(spec.commandLine(), optionName + " must have a host: " + urlStr);
    }
    if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
      throw new ParameterException(
          spec.commandLine(), optionName + " must not contain userinfo (for security): " + urlStr);
    }
    return uri;
  }
}
