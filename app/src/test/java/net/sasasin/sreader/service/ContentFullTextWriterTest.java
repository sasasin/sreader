package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.repository.ContentFullTextRepository;
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
  void saveIfAbsentReturnsFalseAndSkipsRepositoryForBlankFullText(String fullText) {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);

    assertThat(writer.saveIfAbsent(header, fullText)).isFalse();
    verify(repository, never()).insertIfAbsent(any());
  }

  @Test
  void saveIfAbsentInsertsContentFullTextAndReturnsRepositoryResultTrue() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenReturn(true);

    assertThat(writer.saveIfAbsent(header, "body text")).isTrue();

    ArgumentCaptor<ContentFullText> captor = ArgumentCaptor.forClass(ContentFullText.class);
    verify(repository).insertIfAbsent(captor.capture());
    ContentFullText saved = captor.getValue();
    assertThat(saved.id()).isEqualTo(HashIds.md5(header.url()));
    assertThat(saved.contentHeaderId()).isEqualTo("header-id");
    assertThat(saved.fullText()).isEqualTo("body text");
  }

  @Test
  void saveIfAbsentReturnsRepositoryResultFalse() {
    ContentHeader header =
        new ContentHeader("header-id", "feed-id", "https://example.com/a", "Title", null);
    when(repository.insertIfAbsent(any())).thenReturn(false);

    assertThat(writer.saveIfAbsent(header, "body text")).isFalse();
    verify(repository).insertIfAbsent(any(ContentFullText.class));
  }
}
