package net.sasasin.sreader.service.autopagerize;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses WeData AutoPagerize {@code items_all.json} (top-level array) into validated items.
 * Optional timestamp parse failures become warnings with NULL timestamps (rule is still accepted
 * when core fields are valid). Invalid {@code insertBefore} XPath is a warning only for
 * compatibility.
 */
@Component
public class AutoPagerizeJsonParser {

  private final JsonMapper jsonMapper;
  private final AutoPagerizeUrlPatternCompiler urlPatternCompiler;
  private final AutoPagerizeXPathSyntaxChecker xpathSyntaxChecker;

  public AutoPagerizeJsonParser(
      AutoPagerizeUrlPatternCompiler urlPatternCompiler,
      AutoPagerizeXPathSyntaxChecker xpathSyntaxChecker) {
    this.jsonMapper = JsonMapper.shared();
    this.urlPatternCompiler = urlPatternCompiler;
    this.xpathSyntaxChecker = xpathSyntaxChecker;
  }

  public List<AutoPagerizeParsedItem> parseArray(byte[] utf8Json) {
    Objects.requireNonNull(utf8Json, "utf8Json must not be null");
    final JsonNode root;
    try {
      root = jsonMapper.readTree(utf8Json);
    } catch (JacksonException e) {
      throw new AutoPagerizeImportException("Input is not valid UTF-8 JSON: " + e.getMessage(), e);
    }
    if (root == null || !root.isArray()) {
      throw new AutoPagerizeImportException("Top-level JSON value must be an array");
    }
    if (root.isEmpty()) {
      throw new AutoPagerizeImportException("Input array is empty");
    }

    List<AutoPagerizeParsedItem> items = new ArrayList<>(root.size());
    for (int i = 0; i < root.size(); i++) {
      items.add(parseItem(i, root.get(i)));
    }
    return List.copyOf(items);
  }

  private AutoPagerizeParsedItem parseItem(int ordinal, JsonNode node) {
    String rawItemJson = writeJson(node);
    List<AutoPagerizeIssue> errors = new ArrayList<>();
    List<AutoPagerizeIssue> warnings = new ArrayList<>();

    if (node == null || node.isNull() || !node.isObject()) {
      errors.add(new AutoPagerizeIssue("INVALID_ITEM", "Array element must be a JSON object"));
      return new AutoPagerizeParsedItem(
          ordinal,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          rawItemJson,
          errors,
          warnings);
    }

    String externalId = optionalTextOrNumber(node.get("id"));
    String resourceUrl = blankToNull(textOrNull(node.get("resource_url")));
    String name = blankToNull(textOrNull(node.get("name")));
    String createdBy = blankToNull(textOrNull(node.get("created_by")));
    OffsetDateTime createdAt =
        parseOptionalTimestamp(node.get("created_at"), "created_at", warnings);
    OffsetDateTime updatedAt =
        parseOptionalTimestamp(node.get("updated_at"), "updated_at", warnings);

    JsonNode data = node.get("data");
    String urlPattern = null;
    String nextLink = null;
    String pageElement = null;
    String insertBefore = null;
    String exampleUrl = null;

    if (data == null || data.isNull() || !data.isObject()) {
      errors.add(new AutoPagerizeIssue("MISSING_DATA", "data object is required"));
    } else {
      urlPattern = requiredText(data.get("url"), "url", errors);
      nextLink = requiredText(data.get("nextLink"), "nextLink", errors);
      pageElement = requiredText(data.get("pageElement"), "pageElement", errors);
      insertBefore = blankToNull(textOrNull(data.get("insertBefore")));
      exampleUrl = blankToNull(textOrNull(data.get("exampleUrl")));

      if (urlPattern != null) {
        String regexError = urlPatternCompiler.validateSyntax(urlPattern);
        if (regexError != null) {
          errors.add(
              new AutoPagerizeIssue(
                  "INVALID_URL_PATTERN", "data.url is not a valid regex: " + regexError));
        } else if (exampleUrl != null) {
          Pattern compiled = urlPatternCompiler.compile(urlPattern);
          if (!urlPatternCompiler.sampleMatches(compiled, exampleUrl)) {
            warnings.add(
                new AutoPagerizeIssue(
                    "EXAMPLE_URL_MISMATCH",
                    "data.exampleUrl does not match data.url (Matcher.find)"));
          }
        }
      }
      if (nextLink != null) {
        String xpathError = xpathSyntaxChecker.validateSyntax(nextLink);
        if (xpathError != null) {
          errors.add(
              new AutoPagerizeIssue(
                  "INVALID_NEXT_LINK_XPATH", "data.nextLink is not a valid XPath: " + xpathError));
        }
      }
      if (pageElement != null) {
        String xpathError = xpathSyntaxChecker.validateSyntax(pageElement);
        if (xpathError != null) {
          errors.add(
              new AutoPagerizeIssue(
                  "INVALID_PAGE_ELEMENT_XPATH",
                  "data.pageElement is not a valid XPath: " + xpathError));
        }
      }
      if (insertBefore != null) {
        String xpathError = xpathSyntaxChecker.validateSyntax(insertBefore);
        if (xpathError != null) {
          // Compatibility: keep the rule; runtime does not use insertBefore.
          warnings.add(
              new AutoPagerizeIssue(
                  "INVALID_INSERT_BEFORE_XPATH",
                  "data.insertBefore is not a valid XPath (stored, ignored at runtime): "
                      + xpathError));
        }
      }
    }

    return new AutoPagerizeParsedItem(
        ordinal,
        externalId,
        resourceUrl,
        name,
        createdBy,
        createdAt,
        updatedAt,
        urlPattern,
        nextLink,
        pageElement,
        insertBefore,
        exampleUrl,
        rawItemJson,
        errors,
        warnings);
  }

  private OffsetDateTime parseOptionalTimestamp(
      JsonNode node, String fieldName, List<AutoPagerizeIssue> warnings) {
    if (node == null || node.isNull()) {
      return null;
    }
    String text = textOrNull(node);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(text.trim());
    } catch (DateTimeParseException e) {
      warnings.add(
          new AutoPagerizeIssue(
              "INVALID_" + fieldName.toUpperCase(),
              fieldName + " is not a valid ISO-8601 offset datetime; stored as NULL"));
      return null;
    }
  }

  private static String requiredText(
      JsonNode node, String fieldName, List<AutoPagerizeIssue> errors) {
    String value = textOrNull(node);
    if (value == null || value.isBlank()) {
      errors.add(
          new AutoPagerizeIssue(
              "MISSING_" + fieldName.toUpperCase(), "data." + fieldName + " is required"));
      return null;
    }
    return value.trim();
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asString();
    }
    if (node.isNumber() || node.isBoolean()) {
      return node.asString();
    }
    return null;
  }

  private static String optionalTextOrNumber(JsonNode node) {
    String value = textOrNull(node);
    return blankToNull(value);
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String writeJson(JsonNode node) {
    if (node == null || node.isNull()) {
      return "null";
    }
    try {
      return jsonMapper.writeValueAsString(node);
    } catch (JacksonException e) {
      throw new AutoPagerizeImportException("Failed to serialize raw item JSON", e);
    }
  }
}
