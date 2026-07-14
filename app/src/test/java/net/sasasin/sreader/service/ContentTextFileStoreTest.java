package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentTextFileStoreTest {

  @TempDir Path tempDir;

  @Test
  void deletesExistingFileAndTreatsMissingFileAsSuccess() throws Exception {
    ContentTextFileStore store = new ContentTextFileStore(properties(tempDir));
    String id = "0123456789abcdef0123456789abcdef";
    Files.writeString(tempDir.resolve(id + ".txt"), "body");

    assertThat(store.deleteForHeaderId(id).status()).isEqualTo(ContentTextFileStore.Status.DELETED);
    assertThat(store.deleteForHeaderId(id).status()).isEqualTo(ContentTextFileStore.Status.MISSING);
  }

  @Test
  void rejectsInvalidHeaderIds() {
    ContentTextFileStore store = new ContentTextFileStore(properties(tempDir));

    assertThat(store.deleteForHeaderId(null))
        .isEqualTo(ContentTextFileStore.DeleteResult.failed("Invalid contentHeaderId: null"));
    assertThat(store.deleteForHeaderId("not-an-id").status())
        .isEqualTo(ContentTextFileStore.Status.FAILED);
  }

  @Test
  void reportsIoFailures() throws Exception {
    Path outputFile = tempDir.resolve("not-a-directory");
    Files.writeString(outputFile, "file");
    ContentTextFileStore store = new ContentTextFileStore(properties(outputFile));

    assertThat(store.deleteForHeaderId("0123456789abcdef0123456789abcdef").status())
        .isEqualTo(ContentTextFileStore.Status.FAILED);
  }

  private FeedReaderProperties properties(Path outputDir) {
    return new FeedReaderProperties(
        null,
        null,
        null,
        null,
        new FeedReaderProperties.TextExport(false, outputDir, 10),
        List.of());
  }
}
