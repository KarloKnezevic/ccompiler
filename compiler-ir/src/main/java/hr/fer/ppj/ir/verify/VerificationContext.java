package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.diagnostic.Diagnostic;
import hr.fer.ppj.ir.diagnostic.DiagnosticCollector;
import java.util.List;
import java.util.Objects;

/**
 * Context for collecting IR verification diagnostics.
 *
 * <p>This class wraps a {@link DiagnosticCollector} and provides convenience
 * methods for reporting verification errors with appropriate location context.
 *
 * <p>Invariants:
 * <ul>
 *   <li>All reported errors include function and block context when available</li>
 *   <li>Instruction indices are included when verifying specific instructions</li>
 *   <li>Errors are accumulated until verification completes</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VerificationContext {

  private final DiagnosticCollector collector;

  /**
   * Creates a new verification context with its own diagnostic collector.
   */
  public VerificationContext() {
    this.collector = new DiagnosticCollector();
  }

  /**
   * Creates a verification context that reports to an external collector.
   *
   * @param collector the diagnostic collector to use
   */
  public VerificationContext(DiagnosticCollector collector) {
    this.collector = Objects.requireNonNull(collector, "collector must not be null");
  }

  /**
   * Reports a global-level verification error.
   *
   * @param message the error message
   */
  public void addGlobalError(String message) {
    collector.reportGlobalError(message);
  }

  /**
   * Reports a function-level verification error.
   *
   * @param functionName the function name
   * @param message the error message
   */
  public void addFunctionError(String functionName, String message) {
    collector.reportFunctionError(functionName, message);
  }

  /**
   * Reports a block-level verification error.
   *
   * @param functionName the function name
   * @param blockLabel the block label
   * @param message the error message
   */
  public void addError(String functionName, String blockLabel, String message) {
    collector.reportBlockError(functionName, blockLabel, message);
  }

  /**
   * Reports an instruction-level verification error.
   *
   * @param functionName the function name
   * @param blockLabel the block label
   * @param instructionIndex the instruction index
   * @param message the error message
   */
  public void addInstructionError(
      String functionName, String blockLabel, int instructionIndex, String message) {
    collector.reportInstructionError(functionName, blockLabel, instructionIndex, message);
  }

  /**
   * Returns whether any errors have been reported.
   *
   * @return true if at least one error exists
   */
  public boolean hasErrors() {
    return collector.hasErrors();
  }

  /**
   * Returns all error messages as strings (for backward compatibility).
   *
   * @return list of formatted error strings
   */
  public List<String> getErrors() {
    return collector.getErrors().stream()
        .map(Diagnostic::toString)
        .toList();
  }

  /**
   * Returns the underlying diagnostic collector.
   *
   * @return the diagnostic collector
   */
  public DiagnosticCollector getCollector() {
    return collector;
  }
}
