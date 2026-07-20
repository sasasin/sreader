package net.sasasin.sreader.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.repository.ContentFullTextRepository;
import net.sasasin.sreader.service.article.HashIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ContentFullTextWriterTest {

  private final ContentFullTextRepository repository = mock(ContentFullTextRepository.class);
  private final ContentFullTextWriter writer = new ContentFullTextWriter(repository);

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void saveIfAbsentReturnsNoContentAndSkipsRepositoryForBlankFullText(String fullText) {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);

    assertThat(writer.saveIfAbsent(header, fullText))
        .isEqualTo(ContentFullTextWriteOutcome.NO_CONTENT);
    verify(repository, never()).insertIfAbsent(any());
  }

  @Test
  void saveIfAbsentInsertsContentFullTextAndReturnsInserted() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    assertThat(writer.saveIfAbsent(header, "body text"))
        .isEqualTo(ContentFullTextWriteOutcome.INSERTED);

    ArgumentCaptor<ContentFullText> captor = ArgumentCaptor.forClass(ContentFullText.class);
    verify(repository).insertIfAbsent(captor.capture());
    ContentFullText saved = captor.getValue();
    assertThat(saved.id()).isEqualTo(HashIds.md5(header.canonicalUrl()));
    assertThat(saved.contentHeaderId()).isEqualTo("header-id");
    assertThat(saved.fullText()).isEqualTo("body text");
  }

  @Test
  void saveIfAbsentReturnsAlreadyExistsWhenRepositoryFalse() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenReturn(false);

    assertThat(writer.saveIfAbsent(header, "body text"))
        .isEqualTo(ContentFullTextWriteOutcome.ALREADY_EXISTS);
    verify(repository).insertIfAbsent(any(ContentFullText.class));
  }

  @Test
  void saveIfAbsentDoesNotSwallowRepositoryExceptions() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenThrow(new RuntimeException("db down"));

    assertThatThrownBy(() -> writer.saveIfAbsent(header, "body text"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db down");
  }
}
