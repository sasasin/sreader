package net.sasasin.sreader.domain;

import java.time.OffsetDateTime;

/**
 * Singleton active AutoPagerize dataset pointer ({@code autopagerize_state.id = 1}). When no
 * dataset is active, both fields are null.
 */
public record AutoPagerizeActiveState(Long activeDatasetId, OffsetDateTime activatedAt) {}
