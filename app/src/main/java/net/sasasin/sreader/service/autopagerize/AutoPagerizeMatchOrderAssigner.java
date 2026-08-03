package net.sasasin.sreader.service.autopagerize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.sasasin.sreader.domain.AutoPagerizeRule;

/**
 * Assigns deterministic {@code match_order}: longer {@code url_pattern} first, then lower input
 * ordinal. Does not deduplicate rules.
 */
public final class AutoPagerizeMatchOrderAssigner {

  private AutoPagerizeMatchOrderAssigner() {}

  public static List<AutoPagerizeRule> assign(
      long datasetId, List<AutoPagerizeParsedItem> acceptedItems) {
    Objects.requireNonNull(acceptedItems, "acceptedItems must not be null");
    List<AutoPagerizeParsedItem> sorted = new ArrayList<>(acceptedItems);
    sorted.sort(
        Comparator.comparingInt((AutoPagerizeParsedItem item) -> item.urlPattern().length())
            .reversed()
            .thenComparingInt(AutoPagerizeParsedItem::ordinal));

    List<AutoPagerizeRule> rules = new ArrayList<>(sorted.size());
    for (int matchOrder = 0; matchOrder < sorted.size(); matchOrder++) {
      AutoPagerizeParsedItem item = sorted.get(matchOrder);
      rules.add(
          new AutoPagerizeRule(
              datasetId,
              item.ordinal(),
              matchOrder,
              item.externalId(),
              item.resourceUrl(),
              item.name(),
              item.createdBy(),
              item.sourceCreatedAt(),
              item.sourceUpdatedAt(),
              item.urlPattern(),
              item.nextLinkXpath(),
              item.pageElementXpath(),
              item.insertBeforeXpath(),
              item.exampleUrl(),
              item.rawItemJson()));
    }
    return List.copyOf(rules);
  }
}
