package net.sasasin.sreader.service.autopagerize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoPagerizeJsonParserTest {

  private AutoPagerizeJsonParser parser;

  @BeforeEach
  void setUp() {
    parser =
        new AutoPagerizeJsonParser(
            new AutoPagerizeUrlPatternCompiler(), new AutoPagerizeXPathSyntaxChecker());
  }

  @Test
  void parsesValidMultipleItemsAndKeepsRawJson() {
    byte[] json =
        """
        [
          {
            "id": "1",
            "name": "A",
            "data": {
              "url": "^https://a\\\\.example/",
              "nextLink": "//a[@rel='next']",
              "pageElement": "//div"
            }
          },
          {
            "id": 2,
            "name": "B",
            "data": {
              "url": "^https://b\\\\.example/long/",
              "nextLink": "//a",
              "pageElement": "//p",
              "insertBefore": "//div[@id='x']",
              "exampleUrl": "https://b.example/long/1"
            }
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);

    List<AutoPagerizeParsedItem> items = parser.parseArray(json);

    assertThat(items).hasSize(2);
    assertThat(items.get(0).accepted()).isTrue();
    assertThat(items.get(0).externalId()).isEqualTo("1");
    assertThat(items.get(0).rawItemJson()).contains("\"name\":\"A\"");
    assertThat(items.get(1).accepted()).isTrue();
    assertThat(items.get(1).externalId()).isEqualTo("2");
    assertThat(items.get(1).exampleUrl()).isEqualTo("https://b.example/long/1");
  }

  @Test
  void rejectsMissingRequiredFieldsAndBlankFields() {
    byte[] json =
        """
        [
          {"name":"no-data"},
          {"name":"blank-url","data":{"url":"  ","nextLink":"//a","pageElement":"//div"}},
          {"name":"missing-next","data":{"url":"^https://x/","pageElement":"//div"}}
        ]
        """
            .getBytes(StandardCharsets.UTF_8);

    List<AutoPagerizeParsedItem> items = parser.parseArray(json);

    assertThat(items).hasSize(3);
    assertThat(items.get(0).accepted()).isFalse();
    assertThat(items.get(0).errors()).extracting(AutoPagerizeIssue::code).contains("MISSING_DATA");
    assertThat(items.get(1).accepted()).isFalse();
    assertThat(items.get(1).errors()).extracting(AutoPagerizeIssue::code).contains("MISSING_URL");
    assertThat(items.get(2).accepted()).isFalse();
    assertThat(items.get(2).errors())
        .extracting(AutoPagerizeIssue::code)
        .contains("MISSING_NEXTLINK");
  }

  @Test
  void rejectsInvalidRegexAndInvalidRequiredXpaths() {
    byte[] json =
        """
        [
          {
            "name":"bad-regex",
            "data":{"url":"[","nextLink":"//a","pageElement":"//div"}
          },
          {
            "name":"bad-next",
            "data":{"url":"^https://x/","nextLink":"///","pageElement":"//div"}
          },
          {
            "name":"bad-page",
            "data":{"url":"^https://x/","nextLink":"//a","pageElement":"///"}
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);

    List<AutoPagerizeParsedItem> items = parser.parseArray(json);

    assertThat(items.get(0).errors())
        .extracting(AutoPagerizeIssue::code)
        .contains("INVALID_URL_PATTERN");
    assertThat(items.get(1).errors())
        .extracting(AutoPagerizeIssue::code)
        .contains("INVALID_NEXT_LINK_XPATH");
    assertThat(items.get(2).errors())
        .extracting(AutoPagerizeIssue::code)
        .contains("INVALID_PAGE_ELEMENT_XPATH");
  }

  @Test
  void optionalInvalidTimestampIsWarningNotRejection() {
    byte[] json =
        """
        [
          {
            "name":"ts",
            "created_at":"not-a-date",
            "updated_at":"also-bad",
            "data":{
              "url":"^https://example/",
              "nextLink":"//a[@rel='next']",
              "pageElement":"//div"
            }
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);

    AutoPagerizeParsedItem item = parser.parseArray(json).get(0);

    assertThat(item.accepted()).isTrue();
    assertThat(item.sourceCreatedAt()).isNull();
    assertThat(item.sourceUpdatedAt()).isNull();
    assertThat(item.warnings())
        .extracting(AutoPagerizeIssue::code)
        .contains("INVALID_CREATED_AT", "INVALID_UPDATED_AT");
  }

  @Test
  void exampleUrlMismatchIsWarningOnly() {
    byte[] json =
        """
        [
          {
            "name":"mismatch",
            "data":{
              "url":"^https://example\\\\.com/articles/",
              "nextLink":"//a",
              "pageElement":"//div",
              "exampleUrl":"https://other.example/x"
            }
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);

    AutoPagerizeParsedItem item = parser.parseArray(json).get(0);
    assertThat(item.accepted()).isTrue();
    assertThat(item.warnings())
        .extracting(AutoPagerizeIssue::code)
        .contains("EXAMPLE_URL_MISMATCH");
  }

  @Test
  void invalidInsertBeforeIsWarningOnly() {
    byte[] json =
        """
        [
          {
            "name":"insert",
            "data":{
              "url":"^https://example/",
              "nextLink":"//a",
              "pageElement":"//div",
              "insertBefore":"///"
            }
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);

    AutoPagerizeParsedItem item = parser.parseArray(json).get(0);
    assertThat(item.accepted()).isTrue();
    assertThat(item.insertBeforeXpath()).isEqualTo("///");
    assertThat(item.warnings())
        .extracting(AutoPagerizeIssue::code)
        .contains("INVALID_INSERT_BEFORE_XPATH");
  }

  @Test
  void rejectsNonArrayTopLevelAndEmptyArray() {
    assertThatThrownBy(() -> parser.parseArray("{}".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(AutoPagerizeImportException.class)
        .hasMessageContaining("array");
    assertThatThrownBy(() -> parser.parseArray("[]".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(AutoPagerizeImportException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void rejectsInvalidJsonSyntax() {
    assertThatThrownBy(() -> parser.parseArray("{".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(AutoPagerizeImportException.class)
        .hasMessageContaining("JSON");
  }

  @Test
  void rejectsTrailingJsonValues() {
    byte[] json =
        "[{\"data\":{\"url\":\"^https://example/\",\"nextLink\":\"//a\",\"pageElement\":\"//div\"}}] {}"
            .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser.parseArray(json))
        .isInstanceOf(AutoPagerizeImportException.class)
        .hasMessageContaining("JSON");
  }

  @Test
  void rejectsNonStringRequiredFields() {
    byte[] json =
        "[{\"data\":{\"url\":true,\"nextLink\":123,\"pageElement\":{}}}]"
            .getBytes(StandardCharsets.UTF_8);

    AutoPagerizeParsedItem item = parser.parseArray(json).get(0);

    assertThat(item.accepted()).isFalse();
    assertThat(item.errors())
        .extracting(AutoPagerizeIssue::code)
        .containsExactlyInAnyOrder(
            "INVALID_URL_TYPE", "INVALID_NEXTLINK_TYPE", "INVALID_PAGEELEMENT_TYPE");
  }

  @Test
  void emptyOptionalTimestampFieldsAreIgnored() {
    byte[] json =
        """
        [
          {
            "created_at": "",
            "updated_at": null,
            "data": {
              "url": "^https://example/",
              "nextLink": "//a",
              "pageElement": "//div"
            }
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);
    AutoPagerizeParsedItem item = parser.parseArray(json).get(0);
    assertThat(item.accepted()).isTrue();
    assertThat(item.sourceCreatedAt()).isNull();
    assertThat(item.sourceUpdatedAt()).isNull();
    assertThat(item.warnings()).isEmpty();
  }

  @Test
  void normalizesOptionalEmptyStringsToNull() {
    byte[] json =
        """
        [
          {
            "resource_url":"  ",
            "created_by":"",
            "data":{
              "url":"^https://example/",
              "nextLink":"//a",
              "pageElement":"//div",
              "insertBefore":"   ",
              "exampleUrl":""
            }
          }
        ]
        """
            .getBytes(StandardCharsets.UTF_8);

    AutoPagerizeParsedItem item = parser.parseArray(json).get(0);
    assertThat(item.accepted()).isTrue();
    assertThat(item.resourceUrl()).isNull();
    assertThat(item.createdBy()).isNull();
    assertThat(item.insertBeforeXpath()).isNull();
    assertThat(item.exampleUrl()).isNull();
  }
}
