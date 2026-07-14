package net.sasasin.sreader.domain;

public record ContentTextFileExportTarget(
    String contentHeaderId,
    String canonicalUrl,
    String title,
    String contentFullTextId,
    String fullText) {}
