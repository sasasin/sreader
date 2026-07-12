package net.sasasin.sreader.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

class UrlValidatorTest {

  @ParameterizedTest
  @MethodSource("validUrls")
  void acceptsSupportedUrlsWithoutNormalizing(String input) {
    URI actual = UrlValidator.validateHttpUrl(input, "--url", spec());

    assertEquals(URI.create(input), actual);
  }

  static Stream<String> validUrls() {
    return Stream.of(
        "http://example.com/feed.xml",
        "https://example.com/feed.xml",
        "HtTpS://example.com/mixed",
        "http://localhost:8080/feed",
        "http://127.0.0.1:8080/feed",
        "http://[::1]:8080/feed",
        "https://example.com:8443/a?query=value#fragment");
  }

  @ParameterizedTest
  @MethodSource("blankUrls")
  void rejectsBlankUrls(String input) {
    ParameterException exception =
        assertThrows(
            ParameterException.class, () -> UrlValidator.validateHttpUrl(input, "--url", spec()));

    assertTrue(exception.getMessage().contains("--url must not be blank"));
  }

  static Stream<String> blankUrls() {
    return Stream.of(null, "", "   ", "\t\n");
  }

  @ParameterizedTest
  @MethodSource("syntaxErrors")
  void syntaxFailurePreservesUriSyntaxExceptionAsCause(String input) {
    ParameterException exception =
        assertThrows(
            ParameterException.class,
            () -> UrlValidator.validateHttpUrl(input, "--site-url", spec()));

    assertTrue(exception.getMessage().contains("Invalid URL for --site-url"));
    assertTrue(exception.getCause() instanceof URISyntaxException);
  }

  static Stream<String> syntaxErrors() {
    return Stream.of(
        "https://example.com/has space",
        "https://[::1",
        "http://example.com/%zz",
        "http://",
        "https://");
  }

  @ParameterizedTest
  @MethodSource("unsupportedSchemes")
  void rejectsUnsupportedSchemes(String input) {
    ParameterException exception =
        assertThrows(
            ParameterException.class, () -> UrlValidator.validateHttpUrl(input, "--url", spec()));

    assertTrue(exception.getMessage().contains("--url must use http or https scheme"));
  }

  static Stream<String> unsupportedSchemes() {
    return Stream.of(
        "relative/path",
        "//example.com/feed",
        "ftp://example.com/feed",
        "file:///tmp/feed",
        "mailto:test@example.com",
        "javascript:alert(1)",
        "data:text/plain,hello");
  }

  @ParameterizedTest
  @MethodSource("missingHostUrls")
  void rejectsHttpUrlsWithoutHosts(String input) {
    ParameterException exception =
        assertThrows(
            ParameterException.class, () -> UrlValidator.validateHttpUrl(input, "--url", spec()));

    assertTrue(exception.getMessage().contains("--url must have a host"));
  }

  static Stream<String> missingHostUrls() {
    return Stream.of("http:relative", "https:opaque", "http:///path", "https:///path");
  }

  @ParameterizedTest
  @MethodSource("userinfoUrls")
  void rejectsNonBlankUserInfo(String input) {
    ParameterException exception =
        assertThrows(
            ParameterException.class,
            () -> UrlValidator.validateHttpUrl(input, "--feed-url", spec()));

    assertTrue(exception.getMessage().contains("--feed-url must not contain userinfo"));
  }

  static Stream<String> userinfoUrls() {
    return Stream.of("http://user@example.com/feed", "https://user:password@example.com/feed");
  }

  @ParameterizedTest
  @MethodSource("currentAcceptedEdgeCases")
  void characterizesCurrentAcceptedEdgeCases(String input) {
    assertEquals(URI.create(input), UrlValidator.validateHttpUrl(input, "--url", spec()));
  }

  static Stream<String> currentAcceptedEdgeCases() {
    return Stream.of(
        "http://@example.com/feed",
        "http://%20@example.com/feed",
        "https://example.com:65536/feed");
  }

  @ParameterizedTest
  @MethodSource("unicodeHostUrls")
  void characterizesUnicodeHostsAsMissingHosts(String input) {
    ParameterException exception =
        assertThrows(
            ParameterException.class, () -> UrlValidator.validateHttpUrl(input, "--url", spec()));

    assertTrue(exception.getMessage().contains("--url must have a host"));
  }

  static Stream<String> unicodeHostUrls() {
    return Stream.of("https://例え.テスト/feed", "https://éxample.com/feed");
  }

  @ParameterizedTest
  @MethodSource("optionNames")
  void parameterExceptionPreservesCommandLineAndOptionName(String optionName) {
    CommandSpec spec = CommandSpec.create();
    CommandLine commandLine = new CommandLine(spec);

    ParameterException exception =
        assertThrows(
            ParameterException.class, () -> UrlValidator.validateHttpUrl("", optionName, spec));

    assertSame(commandLine, exception.getCommandLine());
    assertTrue(exception.getMessage().contains(optionName));
  }

  static Stream<Arguments> optionNames() {
    return Stream.of(Arguments.of("--url"), Arguments.of("--feed-url"), Arguments.of("--site-url"));
  }

  private static CommandSpec spec() {
    CommandSpec spec = CommandSpec.create();
    new CommandLine(spec);
    return spec;
  }
}
