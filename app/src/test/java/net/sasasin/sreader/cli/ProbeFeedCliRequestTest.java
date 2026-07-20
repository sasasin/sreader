package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

class ProbeFeedCliRequestTest {

  @Command(name = "probe-feed-request-test")
  private static final class DummyCommand {}

  private final CommandSpec spec = new CommandLine(new DummyCommand()).getCommandSpec();

  @ParameterizedTest
  @EnumSource(FullTextMethod.class)
  void acceptsAllMethodsWithoutXpath(FullTextMethod method) {
    ProbeFeedCliRequest request =
        ProbeFeedCliRequest.create(
            spec,
            "https://example.com/feed.xml",
            method,
            FeedEntrySelection.first(),
            null,
            false,
            null,
            null);

    assertThat(request.feedUrl()).isEqualTo(URI.create("https://example.com/feed.xml"));
    assertThat(request.method()).isEqualTo(method);
    assertThat(request.selection()).isEqualTo(FeedEntrySelection.first());
    assertThat(request.xpath()).isEmpty();
    assertThat(request.output()).isEmpty();
    assertThat(request.maxChars()).isEmpty();
  }

  @Test
  void rejectsFeedMethodWithXpath() {
    assertThatThrownBy(
            () ->
                ProbeFeedCliRequest.create(
                    spec,
                    "https://example.com/feed.xml",
                    FullTextMethod.FEED,
                    FeedEntrySelection.latest(),
                    "//p",
                    false,
                    null,
                    null))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("--xpath")
        .hasMessageContaining("feed");
  }

  @Test
  void acceptsArticleMethodWithXpath() {
    ProbeFeedCliRequest request =
        ProbeFeedCliRequest.create(
            spec,
            "https://example.com/feed.xml",
            FullTextMethod.HTTP,
            FeedEntrySelection.index(1),
            "//article",
            true,
            "out.txt",
            20);

    assertThat(request.xpath()).contains("//article");
    assertThat(request.selection()).isEqualTo(FeedEntrySelection.index(1));
    assertThat(request.verbose()).isTrue();
    assertThat(request.output()).contains("out.txt");
    assertThat(request.maxChars()).contains(20);
  }

  @Test
  void blankXpathIsAbsenceEvenWithFeedMethod() {
    ProbeFeedCliRequest request =
        ProbeFeedCliRequest.create(
            spec,
            "https://example.com/feed.xml",
            FullTextMethod.FEED,
            FeedEntrySelection.first(),
            "  ",
            false,
            null,
            null);

    assertThat(request.xpath()).isEmpty();
  }

  @Test
  void rejectsNullSelection() {
    assertThatThrownBy(
            () ->
                ProbeFeedCliRequest.create(
                    spec,
                    "https://example.com/feed.xml",
                    FullTextMethod.HTTP,
                    null,
                    null,
                    false,
                    null,
                    null))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("selection");
  }

  @Test
  void rejectsNullMethod() {
    assertThatThrownBy(
            () ->
                ProbeFeedCliRequest.create(
                    spec,
                    "https://example.com/feed.xml",
                    null,
                    FeedEntrySelection.first(),
                    null,
                    false,
                    null,
                    null))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("--method");
  }

  @Test
  void rejectsInvalidFeedUrlUsingCommandSpec() {
    assertThatThrownBy(
            () ->
                ProbeFeedCliRequest.create(
                    spec,
                    "not-a-url",
                    FullTextMethod.HTTP,
                    FeedEntrySelection.first(),
                    null,
                    false,
                    null,
                    null))
        .isInstanceOf(ParameterException.class)
        .satisfies(
            ex -> {
              ParameterException pe = (ParameterException) ex;
              assertThat(pe.getCommandLine()).isSameAs(spec.commandLine());
              assertThat(pe.getMessage()).containsIgnoringCase("http");
            });
  }
}
