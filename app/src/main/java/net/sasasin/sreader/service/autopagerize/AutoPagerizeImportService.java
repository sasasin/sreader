package net.sasasin.sreader.service.autopagerize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.sasasin.sreader.domain.AutoPagerizeFormats;
import net.sasasin.sreader.repository.AutoPagerizeDatasetRepository;
import net.sasasin.sreader.repository.AutoPagerizeStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Imports a local AutoPagerize {@code items_all.json} as an immutable dataset snapshot. Does not
 * fetch URLs. Identity is ({@code format}, SHA-256 of raw bytes, importer version).
 */
@Service
public class AutoPagerizeImportService {

  /** Default maximum input file size (50 MiB). */
  public static final long DEFAULT_MAX_BYTES = 50L * 1024L * 1024L;

  private final AutoPagerizeJsonParser jsonParser;
  private final AutoPagerizeImportPersister persister;
  private final AutoPagerizeDatasetRepository datasetRepository;
  private final AutoPagerizeStateRepository stateRepository;
  private final long maxBytes;

  @Autowired
  public AutoPagerizeImportService(
      AutoPagerizeJsonParser jsonParser,
      AutoPagerizeImportPersister persister,
      AutoPagerizeDatasetRepository datasetRepository,
      AutoPagerizeStateRepository stateRepository) {
    this(jsonParser, persister, datasetRepository, stateRepository, DEFAULT_MAX_BYTES);
  }

  /** Package-visible constructor for tests that need a custom max file size. */
  AutoPagerizeImportService(
      AutoPagerizeJsonParser jsonParser,
      AutoPagerizeImportPersister persister,
      AutoPagerizeDatasetRepository datasetRepository,
      AutoPagerizeStateRepository stateRepository,
      long maxBytes) {
    this.jsonParser = jsonParser;
    this.persister = persister;
    this.datasetRepository = datasetRepository;
    this.stateRepository = stateRepository;
    this.maxBytes = maxBytes;
  }

  public AutoPagerizeImportReport importFile(Path input, AutoPagerizeImportOptions options) {
    Objects.requireNonNull(input, "input must not be null");
    Objects.requireNonNull(options, "options must not be null");
    Path absolute = input.toAbsolutePath().normalize();
    if (!Files.isRegularFile(absolute)) {
      throw new AutoPagerizeImportException("Input is not a regular file: " + absolute);
    }
    final byte[] bytes;
    try {
      long size = Files.size(absolute);
      if (size > maxBytes) {
        throw new AutoPagerizeImportException(
            "Input file exceeds max size of " + maxBytes + " bytes (actual " + size + ")");
      }
      bytes = Files.readAllBytes(absolute);
    } catch (IOException e) {
      throw new AutoPagerizeImportException("Failed to read input file: " + absolute, e);
    }
    String filename = absolute.getFileName() == null ? null : absolute.getFileName().toString();
    return importBytes(bytes, filename, options);
  }

  public AutoPagerizeImportReport importBytes(
      byte[] bytes, String sourceFilename, AutoPagerizeImportOptions options) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    Objects.requireNonNull(options, "options must not be null");
    if (bytes.length > maxBytes) {
      throw new AutoPagerizeImportException(
          "Input exceeds max size of " + maxBytes + " bytes (actual " + bytes.length + ")");
    }

    String sha256 = sha256Hex(bytes);
    List<AutoPagerizeParsedItem> items = jsonParser.parseArray(bytes);
    List<AutoPagerizeParsedItem> accepted =
        items.stream().filter(AutoPagerizeParsedItem::accepted).toList();
    List<AutoPagerizeParsedItem> rejected =
        items.stream().filter(item -> !item.accepted()).toList();

    if (accepted.isEmpty()) {
      throw new AutoPagerizeImportException(
          "No accepted rules in input (input="
              + items.size()
              + ", rejected="
              + rejected.size()
              + ")");
    }

    int warningCount = items.stream().mapToInt(item -> item.warnings().size()).sum();
    int duplicateDiagnosticCount = countExactDuplicates(accepted);
    Map<String, Integer> rejectionReasonCounts = countRejectionReasons(rejected);
    Map<String, Integer> warningReasonCounts = countWarningReasons(items);

    ParsedImportPayload payload =
        new ParsedImportPayload(
            AutoPagerizeFormats.WEDATA_AUTOPAGERIZE_ITEMS_ALL,
            sourceFilename,
            blankToNull(options.sourceUri()),
            sha256,
            AutoPagerizeImporterVersion.CURRENT,
            items.size(),
            accepted,
            rejected,
            warningCount,
            duplicateDiagnosticCount,
            rejectionReasonCounts,
            warningReasonCounts,
            options);

    if (options.strict() && !rejected.isEmpty()) {
      List<String> messages = new ArrayList<>();
      messages.add("strict mode: import aborted because rejected rules are present");
      messages.add("rejected=" + rejected.size());
      if (options.dryRun()) {
        messages.add("dry-run: no database changes");
      }
      return buildReport(payload, null, false, false, false, messages);
    }

    if (options.dryRun()) {
      return buildReport(
          payload, null, false, false, true, List.of("dry-run: no database changes"));
    }

    return persister.persist(payload);
  }

  public long activateDataset(long datasetId) {
    return persister.activateDataset(datasetId);
  }

  public List<net.sasasin.sreader.domain.AutoPagerizeDatasetSummary> listDatasets() {
    return datasetRepository.listNewestFirst();
  }

  public Optional<Long> findActiveDatasetId() {
    return stateRepository.findActiveDatasetId();
  }

  static AutoPagerizeImportReport buildReport(
      ParsedImportPayload payload,
      Long datasetId,
      boolean activated,
      boolean reused,
      boolean success,
      List<String> messages) {
    return new AutoPagerizeImportReport(
        payload.format(),
        payload.sourceFilename(),
        payload.sourceUri(),
        payload.sourceSha256(),
        payload.importerVersion(),
        payload.inputCount(),
        payload.accepted().size(),
        payload.rejected().size(),
        payload.warningCount(),
        payload.duplicateDiagnosticCount(),
        payload.options().dryRun(),
        activated,
        reused,
        payload.options().strict(),
        success,
        datasetId,
        payload.rejectionReasonCounts(),
        payload.warningReasonCounts(),
        messages);
  }

  private static Map<String, Integer> countRejectionReasons(List<AutoPagerizeParsedItem> rejected) {
    Map<String, Integer> counts = new HashMap<>();
    for (AutoPagerizeParsedItem item : rejected) {
      for (AutoPagerizeIssue issue : item.errors()) {
        counts.merge(issue.code(), 1, Integer::sum);
      }
    }
    return Map.copyOf(counts);
  }

  private static Map<String, Integer> countWarningReasons(List<AutoPagerizeParsedItem> items) {
    Map<String, Integer> counts = new HashMap<>();
    for (AutoPagerizeParsedItem item : items) {
      for (AutoPagerizeIssue issue : item.warnings()) {
        counts.merge(issue.code(), 1, Integer::sum);
      }
    }
    return Map.copyOf(counts);
  }

  /**
   * Counts accepted items that share the same core SITEINFO triple with another accepted item
   * (diagnostic only; no dedup).
   */
  private static int countExactDuplicates(List<AutoPagerizeParsedItem> accepted) {
    Set<String> seen = new HashSet<>();
    int duplicates = 0;
    List<AutoPagerizeParsedItem> ordered = new ArrayList<>(accepted);
    ordered.sort(Comparator.comparingInt(AutoPagerizeParsedItem::ordinal));
    for (AutoPagerizeParsedItem item : ordered) {
      String key = item.urlPattern() + "\0" + item.nextLinkXpath() + "\0" + item.pageElementXpath();
      if (!seen.add(key)) {
        duplicates++;
      }
    }
    return duplicates;
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the JDK", e);
    }
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  /** Parse result ready for transactional persistence. */
  record ParsedImportPayload(
      String format,
      String sourceFilename,
      String sourceUri,
      String sourceSha256,
      int importerVersion,
      int inputCount,
      List<AutoPagerizeParsedItem> accepted,
      List<AutoPagerizeParsedItem> rejected,
      int warningCount,
      int duplicateDiagnosticCount,
      Map<String, Integer> rejectionReasonCounts,
      Map<String, Integer> warningReasonCounts,
      AutoPagerizeImportOptions options) {}
}
