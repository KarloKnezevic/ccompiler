package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Facade for semantic analysis over a reconstructed generative parse tree.
 *
 * <p>This class intentionally contains very little business logic. It wires together the parser
 * result (`ParseTree`), the semantic tree representation (`NonTerminalNode`), the hierarchical
 * {@link SymbolTable}, and the {@link SemanticChecker}. Keeping the orchestration logic separate
 * makes the checker easier to test in isolation while providing a tiny and stable API surface for
 * other modules (CLI, tests, integration harnesses).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticAnalyzer {

  /**
   * Entry point used when we still have the parser's {@link ParseTree}. The tree is converted into
   * the semantic representation before delegating to {@link #analyze(NonTerminalNode, PrintStream)}.
   */
  public void analyze(ParseTree parseTree, PrintStream out) {
    Objects.requireNonNull(parseTree, "parseTree must not be null");
    Objects.requireNonNull(out, "out must not be null");

    NonTerminalNode root = new ParseTreeConverter().convert(parseTree);
    analyze(root, out);
  }

  /**
   * Runs semantic analysis starting from {@code <prijevodna_jedinica>}. The method instantiates the
   * global scope and hands control to {@link SemanticChecker}, which performs the actual traversal.
   *
   * <p>Having this method public allows tests to inject handmade trees (bypassing the parser) which
   * proves useful when writing focused semantic unit tests.
   *
   * @param root root of the generative parse tree
   * @param out  output stream for diagnostics
   */
  public void analyze(NonTerminalNode root, PrintStream out) {
    Objects.requireNonNull(root, "root must not be null");
    Objects.requireNonNull(out, "out must not be null");

    SymbolTable globalScope = new SymbolTable();
    SemanticChecker checker = new SemanticChecker(globalScope, out);
    checker.check(root);
  }
}

