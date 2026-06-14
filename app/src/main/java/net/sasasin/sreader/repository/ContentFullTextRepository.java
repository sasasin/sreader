package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;

import java.time.OffsetDateTime;
import net.sasasin.sreader.domain.ContentFullText;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ContentFullTextRepository {

	private final DSLContext dsl;

	public ContentFullTextRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public boolean insertIfAbsent(ContentFullText fullText) {
		OffsetDateTime now = OffsetDateTime.now();
		return dsl.insertInto(CONTENT_FULL_TEXT)
				.set(CONTENT_FULL_TEXT.ID, fullText.id())
				.set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, fullText.contentHeaderId())
				.set(CONTENT_FULL_TEXT.FULL_TEXT, fullText.fullText())
				.set(CONTENT_FULL_TEXT.EXTRACTED_AT, now)
				.set(CONTENT_FULL_TEXT.CREATED_AT, now)
				.set(CONTENT_FULL_TEXT.UPDATED_AT, now)
				.onConflict(CONTENT_FULL_TEXT.ID)
				.doNothing()
				.execute() == 1;
	}
}
