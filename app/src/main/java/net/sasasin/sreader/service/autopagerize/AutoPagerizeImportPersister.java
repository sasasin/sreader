package net.sasasin.sreader.service.autopagerize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.sasasin.sreader.domain.AutoPagerizeDataset;
import net.sasasin.sreader.domain.AutoPagerizeDatasetCreate;
import net.sasasin.sreader.domain.AutoPagerizeRule;
import net.sasasin.sreader.domain.AutoPagerizeRuleCounts;
import net.sasasin.sreader.domain.AutoPagerizeRuleRejection;
import net.sasasin.sreader.repository.AutoPagerizeDatasetRepository;
import net.sasasin.sreader.repository.AutoPagerizeRuleRepository;
import net.sasasin.sreader.repository.AutoPagerizeStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional persistence for AutoPagerize import and activate. Concurrent import/activate is
 * serialized by locking the singleton {@code autopagerize_state} row ({@code FOR UPDATE}).
 */
@Service
public class AutoPagerizeImportPersister {

  private final AutoPagerizeDatasetRepository datasetRepository;
  private final AutoPagerizeRuleRepository ruleRepository;
  private final AutoPagerizeStateRepository stateRepository;
  private final JsonMapper jsonMapper;

  public AutoPagerizeImportPersister(
      AutoPagerizeDatasetRepository datasetRepository,
      AutoPagerizeRuleRepository ruleRepository,
      AutoPagerizeStateRepository stateRepository) {
    this.datasetRepository = datasetRepository;
    this.ruleRepository = ruleRepository;
    this.stateRepository = stateRepository;
    this.jsonMapper = JsonMapper.shared();
  }

  @Transactional
  public AutoPagerizeImportReport persist(AutoPagerizeImportService.ParsedImportPayload payload) {
    stateRepository.lockActiveState();

    Optional<AutoPagerizeDataset> existing =
        datasetRepository.findByIdentity(
            payload.format(), payload.sourceSha256(), payload.importerVersion());

    if (existing.isPresent()) {
      AutoPagerizeDataset dataset = existing.get();
      if (payload.options().strict() && dataset.rejectedRuleCount() > 0) {
        return AutoPagerizeImportService.buildReport(
            payload,
            dataset.id(),
            false,
            true,
            false,
            List.of(
                "strict mode: existing dataset has rejections (dataset_id="
                    + dataset.id()
                    + ", rejected="
                    + dataset.rejectedRuleCount()
                    + ")"));
      }
      boolean activated = false;
      if (!payload.options().noActivate()) {
        stateRepository.activateDataset(dataset.id());
        activated = true;
      }
      List<String> messages = new ArrayList<>();
      messages.add("reused_existing_dataset=true");
      messages.add("dataset_id=" + dataset.id());
      if (activated) {
        messages.add("activated existing dataset");
      } else {
        messages.add("no-activate: active pointer unchanged");
      }
      return AutoPagerizeImportService.buildReport(
          payload, dataset.id(), activated, true, true, messages);
    }

    AutoPagerizeDatasetCreate create =
        new AutoPagerizeDatasetCreate(
            payload.format(),
            payload.sourceFilename(),
            payload.sourceUri(),
            payload.sourceSha256(),
            payload.importerVersion(),
            payload.inputCount(),
            payload.accepted().size(),
            payload.rejected().size(),
            "{}");
    long datasetId = datasetRepository.insert(create);

    List<AutoPagerizeRule> rules =
        AutoPagerizeMatchOrderAssigner.assign(datasetId, payload.accepted());
    List<AutoPagerizeRuleRejection> rejections =
        payload.rejected().stream()
            .map(
                item ->
                    new AutoPagerizeRuleRejection(
                        datasetId,
                        item.ordinal(),
                        item.name(),
                        item.rawItemJson(),
                        toErrorsJson(item.errors())))
            .toList();

    ruleRepository.insertRules(rules);
    ruleRepository.insertRejections(rejections);

    AutoPagerizeRuleCounts counts = ruleRepository.countByDatasetId(datasetId);
    if (counts.acceptedRuleCount() != payload.accepted().size()
        || counts.rejectedRuleCount() != payload.rejected().size()) {
      throw new AutoPagerizeImportException(
          "Count integrity check failed for dataset "
              + datasetId
              + ": accepted="
              + counts.acceptedRuleCount()
              + "/"
              + payload.accepted().size()
              + ", rejected="
              + counts.rejectedRuleCount()
              + "/"
              + payload.rejected().size());
    }

    boolean activated = false;
    if (!payload.options().noActivate()) {
      stateRepository.activateDataset(datasetId);
      activated = true;
    }

    List<String> messages = new ArrayList<>();
    messages.add("created_dataset_id=" + datasetId);
    if (activated) {
      messages.add("activated new dataset");
    } else {
      messages.add("no-activate: active pointer unchanged");
    }
    return AutoPagerizeImportService.buildReport(
        payload, datasetId, activated, false, true, messages);
  }

  @Transactional
  public long activateDataset(long datasetId) {
    stateRepository.lockActiveState();
    stateRepository.activateDataset(datasetId);
    return datasetId;
  }

  private String toErrorsJson(List<AutoPagerizeIssue> errors) {
    List<Map<String, String>> payload = new ArrayList<>(errors.size());
    for (AutoPagerizeIssue issue : errors) {
      Map<String, String> entry = new LinkedHashMap<>();
      entry.put("code", issue.code());
      entry.put("message", issue.message());
      payload.add(entry);
    }
    try {
      return jsonMapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      throw new AutoPagerizeImportException("Failed to serialize rejection errors", e);
    }
  }
}
