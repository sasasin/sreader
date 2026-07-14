package net.sasasin.sreader.domain;

import java.util.List;

public record ContentCanonicalizationGroup(
    String canonicalUrl, List<ContentCanonicalizationMember> members, int exportHistoryRows) {}
