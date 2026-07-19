package net.sasasin.sreader.cli;

final class CliExitCodes {

  static final int SUCCESS = 0;
  static final int EXECUTION_ERROR = 1;
  static final int USAGE_ERROR = 2;
  static final int NO_MATCHING_ENTRY = 3;
  static final int EMPTY_RESULT = 4;
  static final int PLAYWRIGHT_DISABLED = 5;

  private CliExitCodes() {}
}
