package net.sasasin.sreader.service;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured details for an expected application-operation failure. Not a domain entity; used to
 * carry failure information across service and CLI boundaries.
 */
public record OperationFailure(
    FailureStage stage,
    FailureKind kind,
    String subject,
    String message,
    Optional<Throwable> cause) {

  public OperationFailure {
    Objects.requireNonNull(stage, "stage must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    subject = OutcomePreconditions.requireNonBlank(subject, "subject");
    message = OutcomePreconditions.requireNonBlank(message, "message");
    cause = Objects.requireNonNull(cause, "cause must not be null");
  }

  public static OperationFailure of(
      FailureStage stage, FailureKind kind, String subject, String message) {
    return new OperationFailure(stage, kind, subject, message, Optional.empty());
  }

  public static OperationFailure of(
      FailureStage stage, FailureKind kind, String subject, String message, Throwable cause) {
    return new OperationFailure(stage, kind, subject, message, Optional.of(cause));
  }

  public boolean interrupted() {
    return kind == FailureKind.INTERRUPTED;
  }
}
