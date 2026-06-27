package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentTextFileExportTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentTextFileWriterTest {

  @TempDir Path tempDir;

  @Test
  void writesUtf8TextFileWithoutDirectoryDistribution() throws Exception {
    ContentTextFileWriter writer = new ContentTextFileWriter(properties(tempDir));
    ContentTextFileExportTarget target =
        new ContentTextFileExportTarget(
            "0123456789abcdef0123456789abcdef",
            "https://example.test/article",
            "Article title",
            "11111111111111111111111111111111",
            "Body text");

    ContentTextFileWriter.WriteResult result = writer.write(target);

    assertThat(result.relativePath()).isEqualTo("0123456789abcdef0123456789abcdef.txt");
    Path output = tempDir.resolve(result.relativePath());
    assertThat(output).exists().isRegularFile();
    String content = Files.readString(output, StandardCharsets.UTF_8);
    assertThat(content)
        .isEqualTo(
            "URL: https://example.test/article"
                + System.lineSeparator()
                + "TITLE: Article title"
                + System.lineSeparator()
                + System.lineSeparator()
                + "Body text"
                + System.lineSeparator());
    try (Stream<Path> entries = Files.list(tempDir)) {
      assertThat(entries.filter(Files::isDirectory)).isEmpty();
    }
    assertThat(result.fileSizeBytes()).isEqualTo(Files.size(output));
    assertThat(result.sha256()).isEqualTo(sha256(output));
  }

  @Test
  void writesEmptyTitleWhenTitleIsNull() throws Exception {
    ContentTextFileWriter writer = new ContentTextFileWriter(properties(tempDir));
    ContentTextFileExportTarget target =
        new ContentTextFileExportTarget(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "https://example.test/article",
            null,
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "Body");

    writer.write(target);

    assertThat(Files.readString(tempDir.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.txt")))
        .contains("TITLE: " + System.lineSeparator());
  }

  @Test
  void rejectsInvalidContentHeaderId() {
    ContentTextFileWriter writer = new ContentTextFileWriter(properties(tempDir));
    ContentTextFileExportTarget target =
        new ContentTextFileExportTarget(
            "../bad", "https://example.test/article", "Title", "fullTextId", "Body");

    assertThatThrownBy(() -> writer.write(target)).isInstanceOf(IllegalArgumentException.class);
  }

  private FeedReaderProperties properties(Path outputDir) {
    return new FeedReaderProperties(
        null,
        null,
        null,
        null,
        new FeedReaderProperties.TextExport(true, outputDir, 100),
        List.of());
  }

  private String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(Files.readAllBytes(path));
    return HexFormat.of().formatHex(digest.digest());
  }
}
