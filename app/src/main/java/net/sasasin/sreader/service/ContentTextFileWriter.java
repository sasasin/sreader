package net.sasasin.sreader.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentTextFileExportTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContentTextFileWriter {

  private static final Logger logger = LoggerFactory.getLogger(ContentTextFileWriter.class);
  private static final String CONTENT_HEADER_ID_PATTERN = "[0-9a-f]{32}";

  private final FeedReaderProperties properties;

  public ContentTextFileWriter(FeedReaderProperties properties) {
    this.properties = properties;
  }

  public WriteResult write(ContentTextFileExportTarget target) throws IOException {
    String contentHeaderId = target.contentHeaderId();
    if (contentHeaderId == null || !contentHeaderId.matches(CONTENT_HEADER_ID_PATTERN)) {
      throw new IllegalArgumentException("Invalid contentHeaderId: " + contentHeaderId);
    }

    String relativePath = contentHeaderId + ".txt";
    Path outputDir = properties.textExport().outputDir().normalize();
    Path outputPath = outputDir.resolve(relativePath).normalize();
    if (!outputPath.startsWith(outputDir)) {
      throw new IllegalArgumentException("Invalid output path: " + outputPath);
    }

    Files.createDirectories(outputDir);
    Path temp = Files.createTempFile(outputDir, contentHeaderId + "-", ".tmp");
    try {
      Files.writeString(temp, content(target), StandardCharsets.UTF_8);
      move(temp, outputPath);
      long fileSizeBytes = Files.size(outputPath);
      String sha256 = sha256(outputPath);
      return new WriteResult(relativePath, fileSizeBytes, sha256);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private String content(ContentTextFileExportTarget target) {
    String title = target.title() == null ? "" : target.title();
    return "URL: "
        + target.canonicalUrl()
        + System.lineSeparator()
        + "TITLE: "
        + title
        + System.lineSeparator()
        + System.lineSeparator()
        + target.fullText()
        + System.lineSeparator();
  }

  private void move(Path temp, Path outputPath) throws IOException {
    try {
      Files.move(
          temp, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      logger.warn("Atomic move is not supported for content text export; falling back", e);
      Files.move(temp, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String sha256(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(Files.readAllBytes(path));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public record WriteResult(String relativePath, long fileSizeBytes, String sha256) {}
}
