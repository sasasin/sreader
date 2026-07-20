package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

/**
 * Guards that exclusive CLI option relations stay expressed via Picocli {@link ArgGroup} and typed
 * request factories, not manual priority chains or {@code new CommandLine(this)}.
 */
class CliOptionConstraintArchitectureTest {

  @Test
  void contentCanonicalizeUsesOptionalExclusiveExecutionModeGroup() {
    Field groupField = findArgGroupField(ContentCanonicalizeCommand.class);
    ArgGroup argGroup = groupField.getAnnotation(ArgGroup.class);
    assertThat(argGroup.exclusive()).isTrue();
    assertThat(argGroup.multiplicity()).isEqualTo("0..1");
    assertThat(optionNames(groupField.getType())).containsExactlyInAnyOrder("--dry-run", "--apply");
  }

  @Test
  void probeFeedUsesOptionalExclusiveEntrySelectionGroup() {
    Field groupField = findArgGroupField(ProbeFeedCommand.class);
    ArgGroup argGroup = groupField.getAnnotation(ArgGroup.class);
    assertThat(argGroup.exclusive()).isTrue();
    assertThat(argGroup.multiplicity()).isEqualTo("0..1");
    assertThat(optionNames(groupField.getType()))
        .containsExactlyInAnyOrder(
            "--entry", "--entry-index", "--entry-title-regex", "--entry-url-regex");
  }

  @Test
  void contentCanonicalizeSourceDoesNotReintroduceManualModeValidation() throws IOException {
    String source = readMain("cli/ContentCanonicalizeCommand.java");
    assertThat(source).contains("@ArgGroup");
    assertThat(source).contains("exclusive = true");
    assertThat(source).contains("multiplicity = \"0..1\"");
    // User-input mutual exclusivity must not be reimplemented as ParameterException.
    assertThat(source).doesNotContain("--dry-run and --apply are mutually exclusive");
    assertThat(source).doesNotContain("new CommandLine(this)");
    assertThat(source).doesNotContain("new picocli.CommandLine(this)");
    assertThat(source).doesNotContain("private boolean dryRun");
    assertThat(source).doesNotContain("private boolean apply");
  }

  @Test
  void probeFeedSourceDoesNotReintroducePrioritySelection() throws IOException {
    String source = readMain("cli/ProbeFeedCommand.java");
    assertThat(source).contains("@ArgGroup");
    assertThat(source).contains("exclusive = true");
    assertThat(source).contains("multiplicity = \"0..1\"");
    assertThat(source).doesNotContain("resolveSelection");
    assertThat(source.toLowerCase()).doesNotContain("selection priority");
    assertThat(source).doesNotContain("private String entry;");
    assertThat(source).doesNotContain("private Integer entryIndex;");
    assertThat(source).doesNotContain("private String entryTitleRegex;");
    assertThat(source).doesNotContain("private String entryUrlRegex;");
    assertThat(source).contains("ProbeFeedCliRequest");
  }

  @Test
  void probeArticleUsesTypedRequestFactory() throws IOException {
    String source = readMain("cli/ProbeArticleCommand.java");
    assertThat(source).contains("ProbeArticleCliRequest");
    assertThat(source).doesNotContain("supportsArticleProbe()");
    assertThat(mainJavaRoot().resolve("cli/ProbeArticleCliRequest.java")).isRegularFile();
    assertThat(mainJavaRoot().resolve("cli/ProbeFeedCliRequest.java")).isRegularFile();
  }

  @Test
  void probeRequestsKeepOptionalOutputAndMaxCharsNonNull() throws IOException {
    assertRequestUsesOptionalOutputAndMaxChars("cli/ProbeArticleCliRequest.java");
    assertRequestUsesOptionalOutputAndMaxChars("cli/ProbeFeedCliRequest.java");
  }

  @Test
  void feedImportDoesNotExclusiveGroupDryRunAndResubscribe() throws IOException {
    String source = readMain("cli/FeedImportCommand.java");
    assertThat(source).doesNotContain("@ArgGroup");
    assertThat(source).contains("--dry-run");
    assertThat(source).contains("--resubscribe");
  }

  private static Field findArgGroupField(Class<?> commandClass) {
    Field[] fields =
        Arrays.stream(commandClass.getDeclaredFields())
            .filter(field -> field.getAnnotation(ArgGroup.class) != null)
            .toArray(Field[]::new);
    assertThat(fields).as("exactly one @ArgGroup on %s", commandClass.getSimpleName()).hasSize(1);
    return fields[0];
  }

  private static Set<String> optionNames(Class<?> groupType) {
    return Arrays.stream(groupType.getDeclaredFields())
        .map(field -> field.getAnnotation(Option.class))
        .filter(option -> option != null)
        .flatMap(option -> Arrays.stream(option.names()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static String readMain(String relative) throws IOException {
    return Files.readString(mainJavaRoot().resolve(relative), StandardCharsets.UTF_8);
  }

  private static void assertRequestUsesOptionalOutputAndMaxChars(String relative)
      throws IOException {
    String source = readMain(relative);
    assertThat(source).contains("private final Optional<String> output;");
    assertThat(source).contains("private final Optional<Integer> maxChars;");
    assertThat(source).contains("this.output = Objects.requireNonNull(output, \"output\");");
    assertThat(source).contains("this.maxChars = Objects.requireNonNull(maxChars, \"maxChars\");");
  }

  private static Path mainJavaRoot() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path fromAppModule = cwd.resolve("src/main/java/net/sasasin/sreader");
    if (Files.isDirectory(fromAppModule)) {
      return fromAppModule;
    }
    Path fromRepoRoot = cwd.resolve("app/src/main/java/net/sasasin/sreader");
    if (Files.isDirectory(fromRepoRoot)) {
      return fromRepoRoot;
    }
    throw new AssertionError("main java root not found from working directory: " + cwd);
  }
}
