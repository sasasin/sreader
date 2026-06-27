package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentTextFileExportTarget;
import net.sasasin.sreader.repository.ContentTextFileExportRepository;
import org.junit.jupiter.api.Test;

class ContentTextFileExportServiceTest {

  @Test
  void disabledExportDoesNothing() {
    ContentTextFileExportRepository repository = mock(ContentTextFileExportRepository.class);
    ContentTextFileWriter writer = mock(ContentTextFileWriter.class);
    ContentTextFileExportService service =
        new ContentTextFileExportService(properties(false), repository, writer);

    assertThat(service.exportPending(100)).isZero();

    verify(repository, never()).findUnexported(100);
  }

  @Test
  void exportsTargetsAndCountsInsertedRecords() throws Exception {
    ContentTextFileExportRepository repository = mock(ContentTextFileExportRepository.class);
    ContentTextFileWriter writer = mock(ContentTextFileWriter.class);
    ContentTextFileExportTarget first = target("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    ContentTextFileExportTarget second = target("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    when(repository.findUnexported(100)).thenReturn(List.of(first, second));
    when(writer.write(first))
        .thenReturn(
            new ContentTextFileWriter.WriteResult(first.contentHeaderId() + ".txt", 10, "sha"));
    when(writer.write(second))
        .thenReturn(
            new ContentTextFileWriter.WriteResult(second.contentHeaderId() + ".txt", 11, "sha"));
    when(repository.insertExported(
            first.contentHeaderId(),
            first.contentFullTextId(),
            first.contentHeaderId() + ".txt",
            10,
            "sha"))
        .thenReturn(true);
    when(repository.insertExported(
            second.contentHeaderId(),
            second.contentFullTextId(),
            second.contentHeaderId() + ".txt",
            11,
            "sha"))
        .thenReturn(false);
    ContentTextFileExportService service =
        new ContentTextFileExportService(properties(true), repository, writer);

    assertThat(service.exportPending(100)).isEqualTo(1);
  }

  @Test
  void writerFailureDoesNotRecordExportAndNextTargetStillRuns() throws Exception {
    ContentTextFileExportRepository repository = mock(ContentTextFileExportRepository.class);
    ContentTextFileWriter writer = mock(ContentTextFileWriter.class);
    ContentTextFileExportTarget failed = target("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    ContentTextFileExportTarget succeeded = target("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    when(repository.findUnexported(100)).thenReturn(List.of(failed, succeeded));
    when(writer.write(failed)).thenThrow(new IOException("write failed"));
    when(writer.write(succeeded))
        .thenReturn(
            new ContentTextFileWriter.WriteResult(succeeded.contentHeaderId() + ".txt", 11, "sha"));
    when(repository.insertExported(
            succeeded.contentHeaderId(),
            succeeded.contentFullTextId(),
            succeeded.contentHeaderId() + ".txt",
            11,
            "sha"))
        .thenReturn(true);
    ContentTextFileExportService service =
        new ContentTextFileExportService(properties(true), repository, writer);

    assertThat(service.exportPending(100)).isEqualTo(1);
    verify(repository, never())
        .insertExported(
            anyString(), eq(failed.contentFullTextId()), anyString(), anyLong(), anyString());
  }

  private FeedReaderProperties properties(boolean enabled) {
    return new FeedReaderProperties(
        null,
        null,
        null,
        null,
        new FeedReaderProperties.TextExport(enabled, Path.of("/tmp/sreader-test"), 100),
        List.of());
  }

  private ContentTextFileExportTarget target(String id) {
    return new ContentTextFileExportTarget(
        id, "https://example.test/" + id, "Title", "full-text-" + id, "Body");
  }
}
