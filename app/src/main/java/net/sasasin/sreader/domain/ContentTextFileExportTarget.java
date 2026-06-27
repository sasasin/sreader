package net.sasasin.sreader.domain;

public record ContentTextFileExportTarget(
    String contentHeaderId, String url, String title, String contentFullTextId, String fullText) {}
