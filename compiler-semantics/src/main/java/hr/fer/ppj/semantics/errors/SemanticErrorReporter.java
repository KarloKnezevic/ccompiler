package hr.fer.ppj.semantics.errors;

import hr.fer.ppj.common.diagnostic.Diagnostic;
import hr.fer.ppj.common.diagnostic.DiagnosticReporter;
import hr.fer.ppj.common.diagnostic.Severity;
import hr.fer.ppj.common.diagnostic.Stage;
import hr.fer.ppj.common.source.SourceLocation;
import hr.fer.ppj.semantics.analysis.ProductionFormatter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.Objects;

/**
 * Centralized semantic error reporting for the semantic analyzer.
 * 
 * <p>
 * This class provides a single point for reporting semantic errors, ensuring
 * consistent error message formatting and preventing duplicate error reports.
 * 
 * <p>
 * All semantic errors are reported by printing the production where the error
 * occurred in the format required by PPJ specification, followed by an empty
 * line.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticErrorReporter {

  private final DiagnosticReporter reporter;
  private boolean errorReported;

  /**
   * Creates a new error reporter that reports to the specified diagnostic
   * reporter.
   * 
   * @param reporter the diagnostic reporter
   * @throws NullPointerException if reporter is null
   */
  public SemanticErrorReporter(DiagnosticReporter reporter) {
    this.reporter = Objects.requireNonNull(reporter, "reporter must not be null");
    this.errorReported = false;
  }

  /**
   * Reports a semantic error at the specified node.
   * 
   * <p>
   * This method reports the production where the error occurred and throws
   * a {@link SemanticException} to terminate semantic analysis. Only the first
   * error is reported; subsequent calls will throw immediately.
   * 
   * @param node the parse node where the error occurred
   * @throws SemanticException always thrown after reporting the error
   */
  public void reportError(NonTerminalNode node) {
    if (errorReported) {
      throw new SemanticException("error already reported");
    }
    errorReported = true;

    int line = findLine(node);
    String message = ProductionFormatter.formatProduction(node);

    reporter.report(new Diagnostic(Stage.SEMANTICS, Severity.ERROR, new SourceLocation(line, 0), message));

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
   * <p>
   * This method is used for program-level semantic errors that don't correspond
   * to a specific parse node, such as missing main function or undefined
   * functions.
   * 
   * @param message the error message to print
   * @throws SemanticException always thrown after reporting the error
   */
  public void reportGlobalError(String message) {
    if (errorReported) {
      throw new SemanticException("error already reported");
    }
    errorReported = true;

    reporter.report(new Diagnostic(Stage.SEMANTICS, Severity.ERROR, new SourceLocation(0, 0), message));

    throw new SemanticException("semantic error");
  }

  /**
   * Resets the error state. This should only be used for testing purposes.
   */
  void reset() {
    this.errorReported = false;
  }

  private int findLine(ParseNode node) {
    if (node instanceof TerminalNode terminal) {
      return terminal.line();
    }
    if (node instanceof NonTerminalNode nonTerminal) {
      if (!nonTerminal.children().isEmpty()) {
        return findLine(nonTerminal.children().get(0));
      }
    }
    return 0;
  }
}
