package hr.fer.ppj.ir.diagnostic;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single diagnostic message from IR generation or verification.
 *
 * <p>Diagnostics capture errors, warnings, and informational messages with
 * precise location context including function name, block label, and
 * instruction index when applicable.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param severity the severity level of the diagnostic
 * @param functionName the function where the issue occurred (may be null for global issues)
 * @param blockLabel the block label where the issue occurred (may be null)
 * @param instructionIndex the instruction index within the block (may be null)
 * @param message the human-readable diagnostic message
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record Diagnostic(
    Severity severity,
    String functionName,
    String blockLabel,
    Integer instructionIndex,
    String message) {

  /**
   * Diagnostic severity levels.
   */
  public enum Severity {
    /** Informational message, does not indicate a problem. */
    INFO,
    /** Warning that may indicate a potential issue. */
    WARNING,
    /** Error that prevents successful IR generation. */
    ERROR
  }

  /**
   * Creates a diagnostic with full validation.
   */
  public Diagnostic {
    Objects.requireNonNull(severity, "severity must not be null");
    Objects.requireNonNull(message, "message must not be null");
    if (message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }

  /**
   * Creates an error diagnostic for a global-level issue.
   *
   * @param message the error message
   * @return a new error diagnostic
   */
  public static Diagnostic globalError(String message) {
    return new Diagnostic(Severity.ERROR, null, null, null, message);
  }

  /**
   * Creates an error diagnostic for a function-level issue.
   *
   * @param functionName the function name
   * @param message the error message
   * @return a new error diagnostic
   */
  public static Diagnostic functionError(String functionName, String message) {
    return new Diagnostic(Severity.ERROR, functionName, null, null, message);
  }

  /**
   * Creates an error diagnostic for a block-level issue.
   *
   * @param functionName the function name
   * @param blockLabel the block label
   * @param message the error message
   * @return a new error diagnostic
   */
  public static Diagnostic blockError(String functionName, String blockLabel, String message) {
    return new Diagnostic(Severity.ERROR, functionName, blockLabel, null, message);
  }

  /**
   * Creates an error diagnostic for an instruction-level issue.
   *
   * @param functionName the function name
   * @param blockLabel the block label
   * @param instructionIndex the instruction index
   * @param message the error message
   * @return a new error diagnostic
   */
  public static Diagnostic instructionError(
      String functionName, String blockLabel, int instructionIndex, String message) {
    return new Diagnostic(Severity.ERROR, functionName, blockLabel, instructionIndex, message);
  }

  /**
   * Returns the function name if present.
   */
  public Optional<String> function() {
    return Optional.ofNullable(functionName);
  }

  /**
   * Returns the block label if present.
   */
  public Optional<String> block() {
    return Optional.ofNullable(blockLabel);
  }

  /**
   * Returns the instruction index if present.
   */
  public Optional<Integer> instruction() {
    return Optional.ofNullable(instructionIndex);
  }

  /**
   * Returns a formatted string representation of this diagnostic.
   *
   * <p>Format: [SEVERITY] location: message
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[").append(severity).append("] ");

    if (functionName != null) {
      sb.append("function '").append(functionName).append("'");
      if (blockLabel != null) {
        sb.append(", block '").append(blockLabel).append("'");
        if (instructionIndex != null) {
          sb.append(", instruction ").append(instructionIndex);
        }
      }
      sb.append(": ");
    }

    sb.append(message);
    return sb.toString();
  }
}
