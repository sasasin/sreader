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

      Flyway finalMigration =
          Flyway.configure()
              .dataSource(dataSource)
              .schemas(schema)
              .defaultSchema(schema)
              .locations("classpath:db/migration")
              .load();
      finalMigration.migrate();

      assertThat(readMethods(schema))
          .containsExactly("playwright_autopagerize", "playwright_autopagerize_readability");
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

  private static String qualified(String schema, String table) {
    return "\"" + schema + "\".\"" + table + "\"";
  }
}
