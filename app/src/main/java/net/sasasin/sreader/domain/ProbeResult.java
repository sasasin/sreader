package net.sasasin.sreader.domain;

import java.net.URI;

public record ProbeResult(
    URI inputUrl, URI finalUrl, String title, FullTextMethod method, String text) {}
