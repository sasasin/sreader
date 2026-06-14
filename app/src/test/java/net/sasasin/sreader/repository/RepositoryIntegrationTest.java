package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.FEED_URL;
import static org.assertj.core.api.Assertions.assertThat;

import net.sasasin.sreader.domain.ContentFullText;
import net.sasasin.sreader.domain.ContentHeader;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RepositoryIntegrationTest {

	@Autowired
	DSLContext dsl;

	@Autowired
	FeedUrlRepository feedUrlRepository;

	@Autowired
	ContentHeaderRepository contentHeaderRepository;

	@Autowired
	ContentFullTextRepository contentFullTextRepository;

	@BeforeEach
	void cleanTables() {
		dsl.deleteFrom(CONTENT_FULL_TEXT).execute();
		dsl.deleteFrom(CONTENT_HEADER).execute();
		dsl.deleteFrom(FEED_URL).execute();
	}

	@Test
	void registersFeedUrlAndSuppressesDuplicates() {
		assertThat(feedUrlRepository.insertIfAbsent("feed0000000000000000000000000001", "https://example.test/rss.xml"))
				.isTrue();
		assertThat(feedUrlRepository.insertIfAbsent("feed0000000000000000000000000001", "https://example.test/rss.xml"))
				.isFalse();
		assertThat(feedUrlRepository.findAll()).hasSize(1);
	}

	@Test
	void savesHeaderAndFullTextOnce() {
		feedUrlRepository.insertIfAbsent("feed0000000000000000000000000002", "https://example.test/feed.xml");
		ContentHeader header = new ContentHeader(
				"head0000000000000000000000000001",
				"feed0000000000000000000000000002",
				"https://example.test/article/1",
				"Article 1",
				null);

		assertThat(contentHeaderRepository.insertIfAbsent(header)).isTrue();
		assertThat(contentHeaderRepository.insertIfAbsent(header)).isFalse();
		assertThat(contentHeaderRepository.findWithoutFullText(10)).hasSize(1);

		ContentFullText fullText = new ContentFullText(
				"text0000000000000000000000000001",
				"head0000000000000000000000000001",
				"Hello body");
		assertThat(contentFullTextRepository.insertIfAbsent(fullText)).isTrue();
		assertThat(contentFullTextRepository.insertIfAbsent(fullText)).isFalse();
		assertThat(contentHeaderRepository.findWithoutFullText(10)).isEmpty();
	}
}
