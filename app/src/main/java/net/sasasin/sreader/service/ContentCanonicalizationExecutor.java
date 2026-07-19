package net.sasasin.sreader.service;

import net.sasasin.sreader.domain.ContentCanonicalizationPlan;
import net.sasasin.sreader.repository.ContentCanonicalizationMaintenanceRepository;

/** DB-only merge operation; transaction and SQL ordering remain in the repository. */
final class ContentCanonicalizationExecutor {
  private final ContentCanonicalizationMaintenanceRepository repository;

  ContentCanonicalizationExecutor(ContentCanonicalizationMaintenanceRepository repository) {
    this.repository = repository;
  }

  ContentCanonicalizationMaintenanceRepository.MergeCounts execute(
      ContentCanonicalizationPlan plan) {
    return repository.merge(plan);
  }
}
