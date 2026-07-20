package net.sasasin.sreader.service.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.springframework.stereotype.Component;

/** Deletes export files only after the corresponding database transaction has committed. */
@Component
public class ContentTextFileStore {

  private static final String CONTENT_HEADER_ID_PATTERN = "[0-9a-f]{32}";

  private final FeedReaderProperties properties;

  public ContentTextFileStore(FeedReaderProperties properties) {
    this.properties = properties;
  }

  public DeleteResult deleteForHeaderId(String contentHeaderId) {
    if (contentHeaderId == null || !contentHeaderId.matches(CONTENT_HEADER_ID_PATTERN)) {
      return DeleteResult.failed("Invalid contentHeaderId: " + contentHeaderId);
    }
    Path outputDir = properties.textExport().outputDir().normalize();
    Path outputPath = outputDir.resolve(contentHeaderId + ".txt").normalize();
    if (!outputPath.startsWith(outputDir)) {
      return DeleteResult.failed("Invalid output path: " + outputPath);
    }
    try {
      return Files.deleteIfExists(outputPath) ? DeleteResult.deleted() : DeleteResult.missing();
    } catch (IOException e) {
      return DeleteResult.failed(e.getMessage());
    }
  }

  public record DeleteResult(Status status, String error) {
    public static DeleteResult deleted() {
      return new DeleteResult(Status.DELETED, null);
    }

    public static DeleteResult missing() {
      return new DeleteResult(Status.MISSING, null);
    }

    public static DeleteResult failed(String error) {
      return new DeleteResult(Status.FAILED, error);
    }
  }

  public enum Status {
    DELETED,
    MISSING,
    FAILED
  }
}
