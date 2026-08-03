package net.sasasin.sreader.cli;

import java.util.concurrent.Callable;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeImportService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "activate",
    description = "Atomically set the active AutoPagerize dataset pointer.",
    mixinStandardHelpOptions = true,
    usageHelpWidth = 100)
@Component
public class AutopagerizeDatasetsActivateCommand implements Callable<Integer> {

  private final AutoPagerizeImportService importService;

  @Option(
      names = "--dataset-id",
      paramLabel = "<ID>",
      description = "Dataset ID to activate (required)",
      required = true)
  private long datasetId;

  public AutopagerizeDatasetsActivateCommand(AutoPagerizeImportService importService) {
    this.importService = importService;
  }

  @Override
  public Integer call() {
    try {
      long activated = importService.activateDataset(datasetId);
      System.out.println("activated_dataset_id=" + activated);
      return CliExitCodes.SUCCESS;
    } catch (IllegalArgumentException e) {
      System.err.println("autopagerize datasets activate failed: " + e.getMessage());
      return CliExitCodes.EXECUTION_ERROR;
    }
  }
}
