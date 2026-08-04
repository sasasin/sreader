package net.sasasin.sreader.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class V12RemoveInfyScrollMigrationIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void migratesLegacyMethodsAndEnforcesFinalConstraint() throws SQLException {
    String schema = "phase7_migration_" + UUID.randomUUID().toString().replace("-", "");
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("11"))
            .cleanDisabled(false)
            .load();
    try {
      flyway.migrate();
      insertFeed(
          schema,
          "feedmiginfy00000000000000000001",
          "https://example.test/legacy",
          "playwright_infy_scroll");
      insertFeed(
          schema,
          "feedmiginfy00000000000000000002",
          "https://example.test/legacy-readability",
          "playwright_infy_scroll_readability");
      insertExistingFullText(schema);
      assertThat(countRows(schema, "autopagerize_dataset")).isZero();
      assertThat(activeDatasetId(schema)).isNull();

      Flyway finalMigration =
          Flyway.configure()
              .dataSource(dataSource)
              .schemas(schema)
              .defaultSchema(schema)
              .locations("classpath:db/migration")
              .load();
      finalMigration.migrate();

      assertThat(readMethods(schema))
          .containsExactly(
              "playwright_autopagerize", "playwright_autopagerize_readability", "http");
      assertThat(readExistingFullText(schema)).isEqualTo("existing full text remains readable");
      assertThat(countRows(schema, "autopagerize_state")).isEqualTo(1);
      assertThat(countRows(schema, "autopagerize_dataset")).isZero();
      assertThat(activeDatasetId(schema)).isNull();
      insertFeed(
          schema,
          "feedmiginfy00000000000000000003",
          "https://example.test/new",
          "playwright_autopagerize");
      assertThatThrownBy(
              () ->
                  insertFeed(
                      schema,
                      "feedmiginfy00000000000000000004",
                      "https://example.test/old-rejected",
                      "playwright_infy_scroll"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("feed_url_full_text_method_check");
    } finally {
      flyway.clean();
    }
  }

  private void insertFeed(String schema, String id, String url, String method) throws SQLException {
    String sql =
        "INSERT INTO "
            + qualified(schema, "feed_url")
            + " (id, url, full_text_method) VALUES (?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      statement.setString(2, url);
      statement.setString(3, method);
      statement.executeUpdate();
    }
  }

  private List<String> readMethods(String schema) throws SQLException {
    String sql = "SELECT full_text_method FROM " + qualified(schema, "feed_url") + " ORDER BY id";
    List<String> methods = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        methods.add(resultSet.getString(1));
      }
    }
    return methods;
  }

  private void insertExistingFullText(String schema) throws SQLException {
    insertFeed(
        schema, "feedmigtext000000000000000000001", "https://example.test/full-text", "http");
    String headerSql =
        "INSERT INTO "
            + qualified(schema, "content_header")
            + " (id, feed_url_id, canonical_url, source_url, fetch_url, title)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    String fullTextSql =
        "INSERT INTO "
            + qualified(schema, "content_full_text")
            + " (id, content_header_id, full_text, extraction_method, source_kind, extracted_url)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement header = connection.prepareStatement(headerSql);
        PreparedStatement fullText = connection.prepareStatement(fullTextSql)) {
      header.setString(1, "headmigtext000000000000000000001");
      header.setString(2, "feedmigtext000000000000000000001");
      header.setString(3, "https://example.test/full-text");
      header.setString(4, "https://example.test/full-text");
      header.setString(5, "https://example.test/full-text");
      header.setString(6, "Existing article");
      header.executeUpdate();
      fullText.setString(1, "textmigtext000000000000000000001");
      fullText.setString(2, "headmigtext000000000000000000001");
      fullText.setString(3, "existing full text remains readable");
      fullText.setString(4, "http");
      fullText.setString(5, "body");
      fullText.setString(6, "https://example.test/full-text");
      fullText.executeUpdate();
    }
  }

  private String readExistingFullText(String schema) throws SQLException {
    String sql =
        "SELECT full_text FROM "
            + qualified(schema, "content_full_text")
            + " WHERE id = 'textmigtext000000000000000000001'";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getString(1);
    }
  }

  private int countRows(String schema, String table) throws SQLException {
    String sql = "SELECT count(*) FROM " + qualified(schema, table);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getInt(1);
    }
  }

  private Long activeDatasetId(String schema) throws SQLException {
    String sql = "SELECT active_dataset_id FROM " + qualified(schema, "autopagerize_state");
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getObject(1, Long.class);
    }
  }

  private static String qualified(String schema, String table) {
    return "\"" + schema + "\".\"" + table + "\"";
  }
}
