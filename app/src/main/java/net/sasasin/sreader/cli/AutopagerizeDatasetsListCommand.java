package net.sasasin.sreader.cli;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.sasasin.sreader.domain.AutoPagerizeDatasetSummary;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Command(
    name = "list",
    description = "List imported AutoPagerize datasets (newest first).",
    mixinStandardHelpOptions = true,
    usageHelpWidth = 120)
@Component
public class AutopagerizeDatasetsListCommand implements Callable<Integer> {

  private final AutoPagerizeImportService importService;

  public AutopagerizeDatasetsListCommand(AutoPagerizeImportService importService) {
    this.importService = importService;
  }

  @Override
  public Integer call() {
    Optional<Long> activeId = importService.findActiveDatasetId();
    List<AutoPagerizeDatasetSummary> datasets = importService.listDatasets();
    if (datasets.isEmpty()) {
      System.out.println("No AutoPagerize datasets.");
      return CliExitCodes.SUCCESS;
    }
    System.out.printf(
        "%-6s %-8s %-24s %-8s %-14s %6s %6s %6s %-24s %s%n",
        "ID",
        "ACTIVE",
        "IMPORTED_AT",
        "FORMAT",
        "SHA256",
        "INPUT",
        "ACC",
        "REJ",
        "FILENAME",
        "SOURCE_URI");
    for (AutoPagerizeDatasetSummary d : datasets) {
      boolean active = activeId.isPresent() && activeId.get().equals(d.id());
      System.out.printf(
          "%-6d %-8s %-24s %-8s %-14s %6d %6d %6d %-24s %s%n",
          d.id(),
          active ? "*" : "",
          d.importedAt(),
          shortFormat(d.format()),
          shortSha(d.sourceSha256()),
          d.inputItemCount(),
          d.acceptedRuleCount(),
          d.rejectedRuleCount(),
          nullToDash(d.sourceFilename()),
          nullToDash(d.sourceUri()));
    }
    return CliExitCodes.SUCCESS;
  }

  private static String shortSha(String sha) {
    if (sha == null || sha.length() < 12) {
      return sha == null ? "-" : sha;
    }
    return sha.substring(0, 12);
  }

  private static String shortFormat(String format) {
    if (format == null) {
      return "-";
    }
    if (format.length() <= 8) {
      return format;
    }
    // wedata-autopagerize-items-all -> wedata
    int dash = format.indexOf('-');
    return dash > 0 ? format.substring(0, dash) : format.substring(0, 8);
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }
}
