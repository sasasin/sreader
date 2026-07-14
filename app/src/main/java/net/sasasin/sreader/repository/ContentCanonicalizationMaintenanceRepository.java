package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.CONTENT_TEXT_FILE_EXPORT;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import net.sasasin.sreader.domain.ContentCanonicalizationGroup;
import net.sasasin.sreader.domain.ContentCanonicalizationMember;
import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class ContentCanonicalizationMaintenanceRepository {

  private final DSLContext dsl;

  public ContentCanonicalizationMaintenanceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<String> findCandidateCanonicalUrls(String host, String after, int limit) {
    Condition condition = CONTENT_HEADER.CANONICAL_URL.isNotNull();
    if (host != null) {
      condition =
          condition.and(
              DSL.lower(CONTENT_HEADER.CANONICAL_URL)
                  .like("https://" + host.toLowerCase(java.util.Locale.ROOT) + "/%"));
    }
    if (after != null) {
      condition = condition.and(CONTENT_HEADER.CANONICAL_URL.gt(after));
    }
    return dsl.select(CONTENT_HEADER.CANONICAL_URL)
        .from(CONTENT_HEADER)
        .where(condition)
        .orderBy(CONTENT_HEADER.CANONICAL_URL.asc())
        .limit(limit)
        .fetch(CONTENT_HEADER.CANONICAL_URL);
  }

  public ContentCanonicalizationGroup loadGroup(String normalizedCanonicalUrl) {
    URI uri = URI.create(normalizedCanonicalUrl);
    String prefix = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
    List<ContentCanonicalizationMember> members =
        dsl.select(
                CONTENT_HEADER.ID,
                CONTENT_HEADER.FEED_URL_ID,
                CONTENT_HEADER.SOURCE_URL,
                CONTENT_HEADER.FETCH_URL,
                CONTENT_HEADER.CANONICAL_URL,
                CONTENT_HEADER.TITLE,
                CONTENT_HEADER.PUBLISHED_AT,
                CONTENT_HEADER.FEED_TEXT,
                CONTENT_HEADER.CREATED_AT,
                CONTENT_HEADER.UPDATED_AT,
                CONTENT_FULL_TEXT.ID,
                CONTENT_FULL_TEXT.FULL_TEXT,
                CONTENT_FULL_TEXT.EXTRACTED_AT,
                CONTENT_FULL_TEXT.CREATED_AT)
            .from(CONTENT_HEADER)
            .leftJoin(CONTENT_FULL_TEXT)
            .on(CONTENT_FULL_TEXT.CONTENT_HEADER_ID.eq(CONTENT_HEADER.ID))
            .where(CONTENT_HEADER.CANONICAL_URL.eq(normalizedCanonicalUrl))
            .or(CONTENT_HEADER.CANONICAL_URL.startsWith(prefix + "?"))
            .fetch(
                record ->
                    new ContentCanonicalizationMember(
                        record.get(CONTENT_HEADER.ID),
                        record.get(CONTENT_HEADER.FEED_URL_ID),
                        record.get(CONTENT_HEADER.SOURCE_URL),
                        record.get(CONTENT_HEADER.FETCH_URL),
                        record.get(CONTENT_HEADER.CANONICAL_URL),
                        record.get(CONTENT_HEADER.TITLE),
                        record.get(CONTENT_HEADER.PUBLISHED_AT),
                        record.get(CONTENT_HEADER.FEED_TEXT),
                        record.get(CONTENT_HEADER.CREATED_AT),
                        record.get(CONTENT_HEADER.UPDATED_AT),
                        record.get(CONTENT_FULL_TEXT.ID),
                        record.get(CONTENT_FULL_TEXT.FULL_TEXT),
                        record.get(CONTENT_FULL_TEXT.EXTRACTED_AT),
                        record.get(CONTENT_FULL_TEXT.CREATED_AT)));
    int exportCount =
        members.isEmpty()
            ? 0
            : dsl.fetchCount(
                CONTENT_TEXT_FILE_EXPORT,
                CONTENT_TEXT_FILE_EXPORT.CONTENT_HEADER_ID.in(
                    members.stream().map(ContentCanonicalizationMember::id).toList()));
    return new ContentCanonicalizationGroup(normalizedCanonicalUrl, members, exportCount);
  }

  /**
   * Applies one group in its own transaction. Files are intentionally handled by the service later.
   */
  public MergeCounts merge(ContentCanonicalizationPlan plan) {
    return dsl.transactionResult(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          List<String> memberIds = plan.memberIds();
          tx.select(CONTENT_HEADER.ID)
              .from(CONTENT_HEADER)
              .where(CONTENT_HEADER.ID.in(memberIds))
              .forUpdate()
              .fetch();

          int exports =
              tx.deleteFrom(CONTENT_TEXT_FILE_EXPORT)
                  .where(CONTENT_TEXT_FILE_EXPORT.CONTENT_HEADER_ID.in(memberIds))
                  .execute();

          ContentCanonicalizationMember values = plan.survivorValues();
          OffsetDateTime now = OffsetDateTime.now();
          tx.insertInto(CONTENT_HEADER)
              .set(CONTENT_HEADER.ID, plan.survivorId())
              .set(CONTENT_HEADER.FEED_URL_ID, values.feedUrlId())
              .set(CONTENT_HEADER.SOURCE_URL, values.sourceUrl())
              .set(CONTENT_HEADER.FETCH_URL, values.fetchUrl())
              .set(CONTENT_HEADER.CANONICAL_URL, plan.group().canonicalUrl())
              .set(CONTENT_HEADER.TITLE, values.title())
              .set(CONTENT_HEADER.PUBLISHED_AT, values.publishedAt())
              .set(CONTENT_HEADER.FEED_TEXT, values.feedText())
              .set(CONTENT_HEADER.CREATED_AT, values.createdAt())
              .set(CONTENT_HEADER.UPDATED_AT, now)
              .onConflict(CONTENT_HEADER.ID)
              .doUpdate()
              .set(CONTENT_HEADER.FEED_URL_ID, values.feedUrlId())
              .set(CONTENT_HEADER.SOURCE_URL, values.sourceUrl())
              .set(CONTENT_HEADER.FETCH_URL, values.fetchUrl())
              .set(CONTENT_HEADER.CANONICAL_URL, plan.group().canonicalUrl())
              .set(CONTENT_HEADER.TITLE, values.title())
              .set(CONTENT_HEADER.PUBLISHED_AT, values.publishedAt())
              .set(CONTENT_HEADER.FEED_TEXT, values.feedText())
              .set(CONTENT_HEADER.CREATED_AT, values.createdAt())
              .set(CONTENT_HEADER.UPDATED_AT, now)
              .execute();

          ContentCanonicalizationMember selected = plan.selectedFullText();
          int fullTexts =
              tx.deleteFrom(CONTENT_FULL_TEXT)
                  .where(CONTENT_FULL_TEXT.CONTENT_HEADER_ID.in(memberIds))
                  .and(
                      selected == null
                          ? DSL.trueCondition()
                          : CONTENT_FULL_TEXT.ID.ne(selected.fullTextId()))
                  .execute();
          if (selected != null) {
            tx.update(CONTENT_FULL_TEXT)
                .set(CONTENT_FULL_TEXT.ID, plan.survivorId())
                .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, plan.survivorId())
                .where(CONTENT_FULL_TEXT.ID.eq(selected.fullTextId()))
                .execute();
          }
          int headers =
              tx.deleteFrom(CONTENT_HEADER)
                  .where(CONTENT_HEADER.ID.in(memberIds))
                  .and(CONTENT_HEADER.ID.ne(plan.survivorId()))
                  .execute();
          return new MergeCounts(headers, fullTexts, exports);
        });
  }

  public record MergeCounts(int deletedHeaders, int deletedFullTexts, int deletedExportHistories) {}
}
