package net.sasasin.sreader.service.autopagerize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.sasasin.sreader.domain.AutoPagerizeDataset;
import net.sasasin.sreader.domain.AutoPagerizeRule;
import net.sasasin.sreader.domain.AutoPagerizeRuleCounts;
import net.sasasin.sreader.repository.AutoPagerizeDatasetRepository;
import net.sasasin.sreader.repository.AutoPagerizeRuleRepository;
import net.sasasin.sreader.repository.AutoPagerizeStateRepository;
import org.springframework.stereotype.Service;

/**
 * Pull-model immutable rule catalog. Active dataset id is re-checked on each active snapshot
 * request; LISTEN/NOTIFY is not used. Holders of an old snapshot may finish work after active
 * switch.
 */
@Service
public class AutoPagerizeRuleCatalog {

  private final AutoPagerizeStateRepository stateRepository;
  private final AutoPagerizeDatasetRepository datasetRepository;
  private final AutoPagerizeRuleRepository ruleRepository;
  private final AutoPagerizeUrlPatternCompiler urlPatternCompiler;
  private final AtomicReference<CachedSnapshot> cache = new AtomicReference<>();

  public AutoPagerizeRuleCatalog(
      AutoPagerizeStateRepository stateRepository,
      AutoPagerizeDatasetRepository datasetRepository,
      AutoPagerizeRuleRepository ruleRepository,
      AutoPagerizeUrlPatternCompiler urlPatternCompiler) {
    this.stateRepository = stateRepository;
    this.datasetRepository = datasetRepository;
    this.ruleRepository = ruleRepository;
    this.urlPatternCompiler = urlPatternCompiler;
  }

  /**
   * @return empty when no dataset is active
   */
  public Optional<AutoPagerizeRuleSnapshot> getActiveSnapshot() {
    Optional<Long> activeId = stateRepository.findActiveDatasetId();
    if (activeId.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(getOrLoad(activeId.get(), true));
  }

  /** Loads (or reuses cache for) a specific dataset snapshot. */
  public AutoPagerizeRuleSnapshot getSnapshot(long datasetId) {
    return getOrLoad(datasetId, false);
  }

  /** Test helper: clears the in-process cache. */
  void clearCache() {
    cache.set(null);
  }

  private AutoPagerizeRuleSnapshot getOrLoad(long datasetId, boolean forActive) {
    CachedSnapshot current = cache.get();
    if (current != null && current.datasetId() == datasetId) {
      return current.snapshot();
    }
    AutoPagerizeRuleSnapshot loaded = loadAndCompile(datasetId);
    if (forActive) {
      // Atomic publish: readers still holding the previous snapshot continue safely.
      cache.set(new CachedSnapshot(datasetId, loaded));
    } else {
      // Non-active lookup may still warm the cache when nothing is cached yet.
      cache.compareAndSet(null, new CachedSnapshot(datasetId, loaded));
      CachedSnapshot after = cache.get();
      if (after != null && after.datasetId() == datasetId) {
        return after.snapshot();
      }
    }
    return loaded;
  }

  private AutoPagerizeRuleSnapshot loadAndCompile(long datasetId) {
    AutoPagerizeDataset dataset =
        datasetRepository
            .findById(datasetId)
            .orElseThrow(
                () ->
                    new AutoPagerizeCatalogException(
                        "AutoPagerize dataset not found: " + datasetId));

    List<AutoPagerizeRule> rules =
        ruleRepository.findRulesByDatasetIdOrderedByMatchOrder(datasetId);
    if (rules.size() != dataset.acceptedRuleCount()) {
      throw new AutoPagerizeCatalogException(
          "Dataset "
              + datasetId
              + " rule count mismatch: stored accepted_rule_count="
              + dataset.acceptedRuleCount()
              + ", loaded="
              + rules.size());
    }
    AutoPagerizeRuleCounts counts = ruleRepository.countByDatasetId(datasetId);
    if (counts.rejectedRuleCount() != dataset.rejectedRuleCount()) {
      throw new AutoPagerizeCatalogException(
          "Dataset "
              + datasetId
              + " rejection count mismatch: stored rejected_rule_count="
              + dataset.rejectedRuleCount()
              + ", loaded="
              + counts.rejectedRuleCount());
    }

    List<CompiledAutoPagerizeRule> compiled = new ArrayList<>(rules.size());
    for (int expectedMatchOrder = 0; expectedMatchOrder < rules.size(); expectedMatchOrder++) {
      AutoPagerizeRule rule = rules.get(expectedMatchOrder);
      if (rule.datasetId() != datasetId || rule.matchOrder() != expectedMatchOrder) {
        throw new AutoPagerizeCatalogException(
            "Dataset "
                + datasetId
                + " match_order mismatch at position "
                + expectedMatchOrder
                + ": loaded dataset_id="
                + rule.datasetId()
                + ", match_order="
                + rule.matchOrder());
      }
      compiled.add(compile(rule));
    }
    return new AutoPagerizeRuleSnapshot(
        dataset.id(), dataset.sourceSha256(), dataset.importerVersion(), compiled);
  }

  private CompiledAutoPagerizeRule compile(AutoPagerizeRule rule) {
    Objects.requireNonNull(rule, "rule must not be null");
    final Pattern pattern;
    try {
      pattern = urlPatternCompiler.compile(rule.urlPattern());
    } catch (PatternSyntaxException e) {
      throw new AutoPagerizeCatalogException(
          "Internal consistency error: failed to compile stored url_pattern for dataset "
              + rule.datasetId()
              + " ordinal "
              + rule.ordinal()
              + ": "
              + rule.urlPattern(),
          e);
    }
    return new CompiledAutoPagerizeRule(
        rule.datasetId(),
        rule.ordinal(),
        rule.matchOrder(),
        rule.name(),
        pattern,
        rule.urlPattern(),
        rule.nextLinkXpath(),
        rule.pageElementXpath(),
        rule.insertBeforeXpath(),
        rule.exampleUrl());
  }

  private record CachedSnapshot(long datasetId, AutoPagerizeRuleSnapshot snapshot) {}
}
