package net.sasasin.sreader.service.autopagerize;

/**
 * Import flags.
 *
 * <ul>
 *   <li>{@code dryRun}: parse/validate/report only; no dataset rows
 *   <li>{@code noActivate}: persist dataset but leave active pointer unchanged
 *   <li>{@code strict}: any rejection aborts without DB changes
 * </ul>
 */
public record AutoPagerizeImportOptions(
    boolean dryRun, boolean noActivate, boolean strict, String sourceUri) {

  public static AutoPagerizeImportOptions defaults() {
    return new AutoPagerizeImportOptions(false, false, false, null);
  }

  public AutoPagerizeImportOptions withDryRun(boolean value) {
    return new AutoPagerizeImportOptions(value, noActivate, strict, sourceUri);
  }

  public AutoPagerizeImportOptions withNoActivate(boolean value) {
    return new AutoPagerizeImportOptions(dryRun, value, strict, sourceUri);
  }

  public AutoPagerizeImportOptions withStrict(boolean value) {
    return new AutoPagerizeImportOptions(dryRun, noActivate, value, sourceUri);
  }

  public AutoPagerizeImportOptions withSourceUri(String value) {
    return new AutoPagerizeImportOptions(dryRun, noActivate, strict, value);
  }
}
