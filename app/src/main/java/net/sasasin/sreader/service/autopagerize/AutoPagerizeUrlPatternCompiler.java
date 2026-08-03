package net.sasasin.sreader.service.autopagerize;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Compiles AutoPagerize {@code data.url} regex. Runtime matching uses {@link
 * java.util.regex.Matcher#find()}, so sample checks use the same API.
 */
@Component
public class AutoPagerizeUrlPatternCompiler {

  public Pattern compile(String urlPattern) {
    Objects.requireNonNull(urlPattern, "urlPattern must not be null");
    return Pattern.compile(urlPattern);
  }

  /**
   * @return empty when compile succeeds; otherwise the syntax error message
   */
  public String validateSyntax(String urlPattern) {
    try {
      compile(urlPattern);
      return null;
    } catch (PatternSyntaxException e) {
      return e.getMessage() == null || e.getMessage().isBlank()
          ? "invalid regular expression"
          : e.getMessage();
    }
  }

  /** Sample match using {@link java.util.regex.Matcher#find()}, matching runtime semantics. */
  public boolean sampleMatches(Pattern pattern, String sampleUrl) {
    Objects.requireNonNull(pattern, "pattern must not be null");
    Objects.requireNonNull(sampleUrl, "sampleUrl must not be null");
    return pattern.matcher(sampleUrl).find();
  }
}
