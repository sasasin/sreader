package net.sasasin.sreader.service.text;

import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentTextFileExportTarget;
import net.sasasin.sreader.repository.ContentTextFileExportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContentTextFileExportService {

  private static final Logger logger = LoggerFactory.getLogger(ContentTextFileExportService.class);

  private final FeedReaderProperties properties;
  private final ContentTextFileExportRepository repository;
  private final ContentTextFileWriter writer;

  public ContentTextFileExportService(
      FeedReaderProperties properties,
      ContentTextFileExportRepository repository,
      ContentTextFileWriter writer) {
    this.properties = properties;
    this.repository = repository;
    this.writer = writer;
  }

  public int exportPending(int limit) {
    if (!properties.textExport().enabled()) {
      return 0;
    }

    logger.info("Starting content text file export phase");
    int exported = 0;
    for (ContentTextFileExportTarget target : repository.findUnexported(limit)) {
      try {
        ContentTextFileWriter.WriteResult result = writer.write(target);
        boolean inserted =
            repository.insertExported(
                target.contentHeaderId(),
                target.contentFullTextId(),
                result.relativePath(),
                result.fileSizeBytes(),
                result.sha256());
        if (inserted) {
          exported++;
        }
      } catch (Exception e) {
        logger.warn(
            "Failed to export content text file for content_header_id={}",
            target.contentHeaderId(),
            e);
      }
    }
    logger.info("Finished content text file export phase: exported={}", exported);
    return exported;
  }
}
