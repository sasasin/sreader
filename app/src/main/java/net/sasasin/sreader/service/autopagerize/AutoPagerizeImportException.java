package net.sasasin.sreader.service.autopagerize;

/** Hard import failure that aborts the whole operation (no dataset written). */
public class AutoPagerizeImportException extends RuntimeException {

  public AutoPagerizeImportException(String message) {
    super(message);
  }

  public AutoPagerizeImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
