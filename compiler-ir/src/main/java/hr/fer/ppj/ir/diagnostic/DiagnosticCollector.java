package hr.fer.ppj.ir.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Collects and manages diagnostics from IR generation and verification.
 *
 * <p>This class accumulates diagnostic messages (errors, warnings, info) during
 * IR processing and provides methods to query the collected diagnostics.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * DiagnosticCollector collector = new DiagnosticCollector();
 * // ... perform IR operations, report diagnostics ...
 * if (collector.hasErrors()) {
 *     throw new IrCompilationException(collector.getErrors());
 * }
 * }</pre>
 *
 * <p>This class is not thread-safe. Use external synchronization if
 * diagnostics are reported from multiple threads.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class DiagnosticCollector {

  private final List<Diagnostic> diagnostics = new ArrayList<>();

  /**
   * Creates a new empty diagnostic collector.
   */
  public DiagnosticCollector() {}

  /**
   * Reports a diagnostic.
   *
   * @param diagnostic the diagnostic to report
   * @throws NullPointerException if diagnostic is null
   */
  public void report(Diagnostic diagnostic) {
    diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic must not be null"));
  }

  /**
   * Reports a global-level error.
   *
   * @param message the error message
   */
  public void reportGlobalError(String message) {
    report(Diagnostic.globalError(message));
  }

  /**
   * Reports a function-level error.
   *
   * @param functionName the function name
   * @param message the error message
   */
  public void reportFunctionError(String functionName, String message) {
    report(Diagnostic.functionError(functionName, message));
  }

  /**
   * Reports a block-level error.
   *
   * @param functionName the function name
   * @param blockLabel the block label
   * @param message the error message
   */
  public void reportBlockError(String functionName, String blockLabel, String message) {
    report(Diagnostic.blockError(functionName, blockLabel, message));
  }

  /**
   * Reports an instruction-level error.
   *
   * @param functionName the function name
   * @param blockLabel the block label
   * @param instructionIndex the instruction index
   * @param message the error message
   */
  public void reportInstructionError(
      String functionName, String blockLabel, int instructionIndex, String message) {
    report(Diagnostic.instructionError(functionName, blockLabel, instructionIndex, message));
  }

  /**
   * Returns whether any errors have been reported.
   *
   * @return true if at least one error diagnostic exists
   */
  public boolean hasErrors() {
    return diagnostics.stream()
        .anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
  }

  /**
   * Returns the count of error diagnostics.
   *
   * @return the number of errors
   */
  public int errorCount() {
    return (int) diagnostics.stream()
        .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
        .count();
  }

  /**
   * Returns all error diagnostics.
   *
   * @return an unmodifiable list of error diagnostics
   */
  public List<Diagnostic> getErrors() {
    return diagnostics.stream()
        .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
        .toList();
  }

  /**
   * Returns all collected diagnostics.
   *
   * @return an unmodifiable list of all diagnostics
   */
  public List<Diagnostic> getAll() {
    return Collections.unmodifiableList(diagnostics);
  }

  /**
   * Returns whether any diagnostics have been reported.
   *
   * @return true if the collector is empty
   */
  public boolean isEmpty() {
    return diagnostics.isEmpty();
  }

  /**
   * Returns the total number of diagnostics.
   *
   * @return the total count
   */
  public int size() {
    return diagnostics.size();
  }

  /**
   * Clears all collected diagnostics.
   */
  public void clear() {
    diagnostics.clear();
  }

  /**
   * Returns a formatted summary of all errors.
   *
   * @return a multi-line string with all error messages
   */
  public String formatErrors() {
    StringBuilder sb = new StringBuilder();
    List<Diagnostic> errors = getErrors();
    sb.append(errors.size()).append(" error(s) found:\n");
    for (Diagnostic error : errors) {
      sb.append("  ").append(error.toString()).append("\n");
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return "DiagnosticCollector{errors=" + errorCount() + ", total=" + size() + "}";
  }
}
