package hr.fer.ppj.ir;

import hr.fer.ppj.ir.lowering.ProgramGenerator;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.print.IrPrettyPrinter;
import hr.fer.ppj.ir.verify.IrVerifier;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Public API facade for IR generation.
 *
 * <p>This class provides a simple interface for:
 * <ul>
 *   <li>Generating IR from semantic analysis results</li>
 *   <li>Printing IR to a string</li>
 *   <li>Verifying IR correctness</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrPipeline {

  private IrPipeline() {}

  /**
   * Generates IR from a semantic program (global scope + semantic tree).
   *
   * @param globalScope the global symbol table from semantic analysis
   * @param semanticTree the semantic tree (rooted at &lt;prijevodna_jedinica&gt;)
   * @return the generated IR program
   */
  public static IrProgram generate(SymbolTable globalScope, NonTerminalNode semanticTree) {
    Objects.requireNonNull(globalScope, "globalScope must not be null");
    Objects.requireNonNull(semanticTree, "semanticTree must not be null");

    ProgramGenerator generator = new ProgramGenerator(globalScope);
    return generator.generate(semanticTree);
  }

  /**
   * Generates IR from a parse tree by running semantic analysis first.
   *
   * <p>This is a convenience method that combines semantic analysis and IR generation.
   *
   * @param parseTree the parse tree from the parser
   * @param out output stream for semantic error messages
   * @return the generated IR program
   * @throws hr.fer.ppj.semantics.errors.SemanticException if semantic analysis fails
   */
  public static IrProgram generate(
      hr.fer.ppj.parser.tree.ParseTree parseTree, PrintStream out) {
    Objects.requireNonNull(parseTree, "parseTree must not be null");
    Objects.requireNonNull(out, "out must not be null");

    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    SemanticAnalyzer.SemanticAnalysisResult result = analyzer.analyzeWithResults(parseTree, out,
        null);
    return generate(result.globalScope(), result.parseTree());
  }

  /**
   * Pretty-prints an IR program to a string.
   *
   * <p>The output follows the IR grammar exactly and is deterministic.
   *
   * @param program the IR program to print
   * @return the pretty-printed IR string
   */
  public static String print(IrProgram program) {
    Objects.requireNonNull(program, "program must not be null");
    return IrPrettyPrinter.print(program);
  }

  /**
   * Verifies an IR program for correctness.
   *
   * <p>Checks invariants such as:
   * <ul>
   *   <li>Every block ends with exactly one terminator</li>
   *   <li>Temps are defined before use</li>
   *   <li>Types match for operations</li>
   *   <li>Address/value correctness</li>
   * </ul>
   *
   * @param program the IR program to verify
   * @throws hr.fer.ppj.ir.verify.IrVerifier.IrVerificationException if verification fails
   */
  public static void verify(IrProgram program) {
    Objects.requireNonNull(program, "program must not be null");
    IrVerifier.verify(program);
  }
}

