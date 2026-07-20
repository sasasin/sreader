package net.sasasin.sreader.service.article;

/** Test fixtures for creating canonicalizers with isolated host and path settings. */
public final class ArticleUrlCanonicalizerFixtures {

  private ArticleUrlCanonicalizerFixtures() {}

  public static ArticleUrlCanonicalizer configuredFor(String host, String pathPrefix) {
    return new ArticleUrlCanonicalizer(host, pathPrefix);
  }
}
