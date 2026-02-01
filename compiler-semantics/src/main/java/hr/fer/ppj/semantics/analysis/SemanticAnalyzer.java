package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.common.diagnostic.DiagnosticReporter;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.errors.SemanticException;
import hr.fer.ppj.semantics.io.SemanticReport;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Facade for semantic analysis over a reconstructed generative parse tree.
 *
 * <p>
 * This class intentionally contains very little business logic. It wires
 * together the parser
 * result (`ParseTree`), the semantic tree representation (`NonTerminalNode`),
 * the hierarchical
 * {@link SymbolTable}, and the {@link SemanticChecker}. Keeping the
 * orchestration logic separate
 * makes the checker easier to test in isolation while providing a tiny and
 * stable API surface for
 * other modules (CLI, tests, integration harnesses).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticAnalyzer {

  /**
   * Entry point used when we still have the parser's {@link ParseTree}. The tree
   * is converted into
   * the semantic representation before delegating to
   * {@link #analyze(NonTerminalNode, DiagnosticReporter)}.
   */
  public void analyze(ParseTree parseTree, DiagnosticReporter reporter) {
    Objects.requireNonNull(parseTree, "parseTree must not be null");
    Objects.requireNonNull(reporter, "reporter must not be null");

    NonTerminalNode root = new ParseTreeConverter().convert(parseTree);
    analyze(root, reporter);
  }

  /**
   * @deprecated Use {@link #analyze(ParseTree, DiagnosticReporter)} instead.
   */
  @Deprecated
  public void analyze(ParseTree parseTree, PrintStream out) {
    analyze(parseTree, createStreamReporter(out));
  }

  /**
   * Entry point with semantic report generation support.
   * 
   * @param parseTree      the parse tree from the parser
   * @param reporter       reporter for error messages
   * @param semanticReport report generator for debug files (null to disable)
   */
  public void analyze(ParseTree parseTree, DiagnosticReporter reporter, SemanticReport semanticReport) {
    Objects.requireNonNull(parseTree, "parseTree must not be null");
    Objects.requireNonNull(reporter, "reporter must not be null");

    NonTerminalNode root = new ParseTreeConverter().convert(parseTree);
    analyze(root, reporter, semanticReport);
  }

  /**
   * @deprecated Use
   *             {@link #analyze(ParseTree, DiagnosticReporter, SemanticReport)}
   *             instead.
   */
  @Deprecated
  public void analyze(ParseTree parseTree, PrintStream out, SemanticReport semanticReport) {
    analyze(parseTree, createStreamReporter(out), semanticReport);
  }

  /**
   * Runs semantic analysis starting from {@code <prijevodna_jedinica>}. The
   * method instantiates the
   * global scope and hands control to {@link SemanticChecker}, which performs the
   * actual traversal.
   *
   * <p>
   * Having this method public allows tests to inject handmade trees (bypassing
   * the parser) which
   * proves useful when writing focused semantic unit tests.
   *
   * @param root     root of the generative parse tree
   * @param reporter reporter for diagnostics
   */
  public void analyze(NonTerminalNode root, DiagnosticReporter reporter) {
    analyze(root, reporter, null);
  }

  /**
   * @deprecated Use {@link #analyze(NonTerminalNode, DiagnosticReporter)}
   *             instead.
   */
  @Deprecated
  public void analyze(NonTerminalNode root, PrintStream out) {
    analyze(root, createStreamReporter(out));
  }

  /**
   * Runs semantic analysis with optional report generation.
   *
   * @param root           root of the generative parse tree
   * @param reporter       reporter for diagnostics
   * @param semanticReport report generator for debug files (null to disable)
   */
  public void analyze(NonTerminalNode root, DiagnosticReporter reporter, SemanticReport semanticReport) {
    Objects.requireNonNull(root, "root must not be null");
    Objects.requireNonNull(reporter, "reporter must not be null");

    SymbolTable globalScope = new SymbolTable();
    SemanticChecker checker = new SemanticChecker(globalScope, reporter);

    try {
      checker.check(root);

      // If we reach here, semantic analysis succeeded (no exceptions thrown)
      // Generate debug files if report generator is provided
      if (semanticReport != null) {
        semanticReport.generateDebugFiles(globalScope, root);
      }

    } catch (SemanticException e) {
      // Semantic error occurred - don't generate debug files
      // The error has already been reported by SemanticChecker
      throw e;
    }
  }

  /**
   * @deprecated Use
   *             {@link #analyze(NonTerminalNode, DiagnosticReporter, SemanticReport)}
   *             instead.
   */
  @Deprecated
  public void analyze(NonTerminalNode root, PrintStream out, SemanticReport semanticReport) {
    analyze(root, createStreamReporter(out), semanticReport);
  }

  /**
   * Runs semantic analysis and returns the results for code generation.
   * 
   * @param parseTree      the parse tree from syntax analysis
   * @param reporter       reporter for diagnostics
   * @param semanticReport report generator for debug files (null to disable)
   * @return the semantic analysis results (global scope and annotated tree)
   * @throws SemanticException if semantic analysis fails
   */
  public SemanticAnalysisResult analyzeWithResults(ParseTree parseTree, DiagnosticReporter reporter,
      SemanticReport semanticReport) {
    Objects.requireNonNull(parseTree, "parseTree must not be null");
    Objects.requireNonNull(reporter, "reporter must not be null");

    NonTerminalNode root = new ParseTreeConverter().convert(parseTree);
    SymbolTable globalScope = new SymbolTable();
    SemanticChecker checker = new SemanticChecker(globalScope, reporter);

    try {
      checker.check(root);

      // If we reach here, semantic analysis succeeded (no exceptions thrown)
      // Generate debug files if report generator is provided
      if (semanticReport != null) {
        semanticReport.generateDebugFiles(globalScope, root);
      }

      // Return results for code generation
      return new SemanticAnalysisResult(globalScope, root);

    } catch (SemanticException e) {
      // Semantic error occurred - don't generate debug files
      // The error has already been reported by SemanticChecker
      throw e;
    }
  }

  /**
   * @deprecated Use
   *             {@link #analyzeWithResults(ParseTree, DiagnosticReporter, SemanticReport)}
   *             instead.
   */
  @Deprecated
  public SemanticAnalysisResult analyzeWithResults(ParseTree parseTree, PrintStream out,
      SemanticReport semanticReport) {
    return analyzeWithResults(parseTree, createStreamReporter(out), semanticReport);
  }

  /**
   * Result of semantic analysis containing the global symbol table and annotated
   * parse tree.
   */
  public record SemanticAnalysisResult(SymbolTable globalScope, NonTerminalNode parseTree) {
  }

  private DiagnosticReporter createStreamReporter(PrintStream out) {
    return new DiagnosticReporter() {
      @Override
      public void report(hr.fer.ppj.common.diagnostic.Diagnostic diagnostic) {
        out.println(diagnostic.message());
        out.println();
      }

      @Override
      public boolean hasErrors() {
        return false;
      }

      @Override
      public java.util.List<hr.fer.ppj.common.diagnostic.Diagnostic> getDiagnostics() {
        return java.util.Collections.emptyList();
      }
    };
  }
}
