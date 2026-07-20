package net.sasasin.sreader.service.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.sasasin.sreader.repository.FeedUrlRepository;
import org.junit.jupiter.api.Test;

class FeedRegistrationServiceTest {

  @Test
  void ignoresBlankCommentsAndLegacyTabFields() {
    FeedUrlRepository repository = mock(FeedUrlRepository.class);
    when(repository.insertIfAbsent(anyString(), anyString())).thenReturn(true, false);
    FeedRegistrationService service = new FeedRegistrationService(repository);

    int inserted =
        service.registerFeedUrls(
            List.of(
                "",
                "# comment",
                "https://example.test/feed.xml\tlegacy-user\tlegacy-password",
                "https://example.test/feed.xml"));

    assertThat(inserted).isEqualTo(1);
  }
}
