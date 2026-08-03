package net.sasasin.sreader.service.autopagerize;

/** Raised when an imported rule cannot be compiled into a runtime catalog entry. */
public class AutoPagerizeCatalogException extends RuntimeException {

  public AutoPagerizeCatalogException(String message) {
    super(message);
  }

  public AutoPagerizeCatalogException(String message, Throwable cause) {
    super(message, cause);
  }
}
