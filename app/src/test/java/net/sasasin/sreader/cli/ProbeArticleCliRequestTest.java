package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

class ProbeArticleCliRequestTest {

  @Command(name = "probe-article-request-test")
  private static final class DummyCommand {}

  private final CommandSpec spec = new CommandLine(new DummyCommand()).getCommandSpec();

  @ParameterizedTest
  @EnumSource(
      value = FullTextMethod.class,
      names = {"FEED"},
      mode = EnumSource.Mode.EXCLUDE)
  void acceptsArticleCapableMethods(FullTextMethod method) {
    ProbeArticleCliRequest request =
        ProbeArticleCliRequest.create(
            spec, "https://example.com/a", method, null, false, null, null);

    assertThat(request.url()).isEqualTo(URI.create("https://example.com/a"));
    assertThat(request.method()).isEqualTo(method);
    assertThat(request.xpath()).isEmpty();
  }

  @Test
  void rejectsFeedMethod() {
    assertThatThrownBy(
            () ->
                ProbeArticleCliRequest.create(
                    spec, "https://example.com/a", FullTextMethod.FEED, null, false, null, null))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("--method feed");
  }

  @Test
  void rejectsNullMethod() {
    assertThatThrownBy(
            () ->
                ProbeArticleCliRequest.create(
                    spec, "https://example.com/a", null, null, false, null, null))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("--method");
  }

  @Test
  void blankXpathIsAbsence() {
    ProbeArticleCliRequest request =
        ProbeArticleCliRequest.create(
            spec, "https://example.com/a", FullTextMethod.HTTP, " \t ", true, "out.txt", 10);

    assertThat(request.xpath()).isEmpty();
    assertThat(request.verbose()).isTrue();
    assertThat(request.output()).isEqualTo("out.txt");
    assertThat(request.maxChars()).isEqualTo(10);
  }

  @Test
  void nonblankXpathIsKeptForSupportingMethods() {
    ProbeArticleCliRequest request =
        ProbeArticleCliRequest.create(
            spec, "https://example.com/a", FullTextMethod.HTTP, "//article", false, null, null);

    assertThat(request.xpath()).contains("//article");
  }

  @Test
  void rejectsInvalidUrlUsingCommandSpec() {
    assertThatThrownBy(
            () ->
                ProbeArticleCliRequest.create(
                    spec, "not-a-url", FullTextMethod.HTTP, null, false, null, null))
        .isInstanceOf(ParameterException.class)
        .satisfies(
            ex -> {
              ParameterException pe = (ParameterException) ex;
              assertThat(pe.getCommandLine()).isSameAs(spec.commandLine());
              assertThat(pe.getMessage()).containsIgnoringCase("http");
            });
  }

  @Test
  void parameterExceptionUsesInjectedCommandLine() {
    try {
      ProbeArticleCliRequest.create(
          spec, "https://example.com/a", FullTextMethod.FEED, null, false, null, null);
    } catch (ParameterException pe) {
      assertThat(pe.getCommandLine()).isSameAs(spec.commandLine());
      return;
    }
    throw new AssertionError("expected ParameterException");
  }
}
