package hr.fer.ppj.semantics.errors;

import hr.fer.ppj.semantics.analysis.ProductionFormatter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Centralized semantic error reporting for the semantic analyzer.
 * 
 * <p>This class provides a single point for reporting semantic errors, ensuring
 * consistent error message formatting and preventing duplicate error reports.
 * 
 * <p>All semantic errors are reported by printing the production where the error
 * occurred in the format required by PPJ specification, followed by an empty line.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticErrorReporter {
  
  private final PrintStream out;
  private boolean errorReported;
  
  /**
   * Creates a new error reporter that writes to the specified output stream.
   * 
   * @param out the output stream for error messages
   * @throws NullPointerException if out is null
   */
  public SemanticErrorReporter(PrintStream out) {
    this.out = Objects.requireNonNull(out, "out must not be null");
    this.errorReported = false;
  }
  
  /**
   * Reports a semantic error at the specified node.
   * 
   * <p>This method prints the production where the error occurred and throws
   * a {@link SemanticException} to terminate semantic analysis. Only the first
   * error is reported; subsequent calls will throw immediately.
   * 
   * @param node the parse node where the error occurred
   * @throws SemanticException always thrown after printing the error
   */
  public void reportError(NonTerminalNode node) {
    if (errorReported) {
      throw new SemanticException("error already reported");
    }
    errorReported = true;
    out.println(ProductionFormatter.formatProduction(node));
    out.println();
    throw new SemanticException("semantic error");
  }
  
  /**
   * Checks whether an error has already been reported.
   * 
   * @return true if an error has been reported, false otherwise
   */
  public boolean hasError() {
    return errorReported;
  }
  
  /**
   * Reports a global constraint error with a custom message.
   * 
   * <p>This method is used for program-level semantic errors that don't correspond
   * to a specific parse node, such as missing main function or undefined functions.
   * 
   * @param message the error message to print
   * @throws SemanticException always thrown after printing the error
   */
  public void reportGlobalError(String message) {
    if (errorReported) {
      throw new SemanticException("error already reported");
    }
    errorReported = true;
    out.println(message);
    out.println();
    throw new SemanticException("semantic error");
  }
  
  /**
   * Resets the error state. This should only be used for testing purposes.
   */
  void reset() {
    this.errorReported = false;
  }
}

