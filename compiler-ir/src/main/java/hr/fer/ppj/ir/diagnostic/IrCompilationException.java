package hr.fer.ppj.ir.diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * Exception thrown when IR generation or verification fails.
 *
 * <p>This exception carries a list of diagnostics that describe the errors
 * encountered during IR processing. It should be thrown when errors are
 * detected to prevent emission of invalid IR.
 *
 * <p>Usage:
 * <pre>{@code
 * if (collector.hasErrors()) {
 *     throw new IrCompilationException(collector.getErrors());
 * }
 * }</pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrCompilationException extends RuntimeException {

  private final List<Diagnostic> diagnostics;

  /**
   * Creates an exception with a list of diagnostics.
   *
   * @param diagnostics the list of error diagnostics
   * @throws NullPointerException if diagnostics is null
   * @throws IllegalArgumentException if diagnostics is empty
   */
  public IrCompilationException(List<Diagnostic> diagnostics) {
    super(formatMessage(diagnostics));
    this.diagnostics = List.copyOf(diagnostics);
  }

  /**
   * Creates an exception with a single error message.
   *
   * @param message the error message
   */
  public IrCompilationException(String message) {
    super(message);
    this.diagnostics = List.of(Diagnostic.globalError(message));
  }

  /**
   * Returns the diagnostics that caused this exception.
   *
   * @return an unmodifiable list of diagnostics
   */
  public List<Diagnostic> getDiagnostics() {
    return diagnostics;
  }

  /**
   * Returns the number of errors.
   *
   * @return the error count
   */
  public int errorCount() {
    return diagnostics.size();
  }

  private static String formatMessage(List<Diagnostic> diagnostics) {
    Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    if (diagnostics.isEmpty()) {
      throw new IllegalArgumentException("diagnostics must not be empty");
    }

    StringBuilder sb = new StringBuilder();
    sb.append("IR compilation failed with ").append(diagnostics.size()).append(" error(s):\n");
    for (Diagnostic d : diagnostics) {
      sb.append("  ").append(d.toString()).append("\n");
    }
    return sb.toString();
  }
}
