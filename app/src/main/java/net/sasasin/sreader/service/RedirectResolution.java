package net.sasasin.sreader.service;

import java.net.URI;
import java.util.Objects;

/** Outcome of resolving redirects for an article or entry URL. */
public sealed interface RedirectResolution
    permits RedirectResolution.Resolved, RedirectResolution.Fallback {

  URI requestedUri();

  URI effectiveUri();

  record Resolved(URI requestedUri, URI effectiveUri) implements RedirectResolution {
    public Resolved {
      Objects.requireNonNull(requestedUri, "requestedUri must not be null");
      Objects.requireNonNull(effectiveUri, "effectiveUri must not be null");
    }
  }

  record Fallback(URI requestedUri, OperationFailure failure) implements RedirectResolution {
    public Fallback {
      Objects.requireNonNull(requestedUri, "requestedUri must not be null");
      Objects.requireNonNull(failure, "failure must not be null");
    }

    @Override
    public URI effectiveUri() {
      return requestedUri;
    }
  }
}
