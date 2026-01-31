package hr.fer.ppj.ir;

import hr.fer.ppj.ir.diagnostic.DiagnosticCollector;
import hr.fer.ppj.ir.diagnostic.IrCompilationException;
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
 * Public API facade for IR generation, verification, and printing.
 *
 * <p>This class provides the main entry point for the IR module. It coordinates:
 * <ul>
 *   <li><b>Generation</b>: Converting semantic analysis results to IR</li>
 *   <li><b>Verification</b>: Validating IR correctness before output</li>
 *   <li><b>Printing</b>: Serializing IR to text format</li>
 * </ul>
 *
 * <h3>Grammar Reference</h3>
 * <p>The generated IR follows the grammar defined in {@code config/ir_definition.txt}.
 *
 * <h3>Error Handling</h3>
 * <p>This API follows a fail-fast strategy. Any error during generation or
 * verification throws {@link IrCompilationException} with detailed diagnostics.
 * Invalid IR is never silently produced.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * IrProgram program = IrPipeline.generate(globalScope, semanticTree);
 * String ir = IrPipeline.print(program);
 * }</pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrPipeline {

  private IrPipeline() {}

  /**
   * Generates and verifies IR from semantic analysis results.
   *
   * <p>This method performs:
   * <ol>
   *   <li>IR generation from the semantic tree</li>
   *   <li>IR verification to ensure correctness</li>
   * </ol>
   *
   * @param globalScope the global symbol table from semantic analysis
   * @param semanticTree the semantic tree (rooted at &lt;prijevodna_jedinica&gt;)
   * @return the generated and verified IR program
   * @throws NullPointerException if any argument is null
   * @throws IrCompilationException if generation or verification fails
   */
  public static IrProgram generate(SymbolTable globalScope, NonTerminalNode semanticTree) {
    Objects.requireNonNull(globalScope, "globalScope must not be null");
    Objects.requireNonNull(semanticTree, "semanticTree must not be null");

    ProgramGenerator generator = new ProgramGenerator(globalScope);
    IrProgram program = generator.generate(semanticTree);

    // Always verify before returning
    IrVerifier.verify(program);

    return program;
  }

  /**
   * Generates IR from a parse tree by running semantic analysis first.
   *
   * <p>This convenience method combines semantic analysis and IR generation.
   * The generated IR is verified before being returned.
   *
   * @param parseTree the parse tree from the parser
   * @param out output stream for semantic error messages
   * @return the generated and verified IR program
   * @throws NullPointerException if any argument is null
   * @throws hr.fer.ppj.semantics.errors.SemanticException if semantic analysis fails
   * @throws IrCompilationException if IR generation or verification fails
   */
  public static IrProgram generate(
      hr.fer.ppj.parser.tree.ParseTree parseTree, PrintStream out) {
    Objects.requireNonNull(parseTree, "parseTree must not be null");
    Objects.requireNonNull(out, "out must not be null");

    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    SemanticAnalyzer.SemanticAnalysisResult result =
        analyzer.analyzeWithResults(parseTree, out, null);

    return generate(result.globalScope(), result.parseTree());
  }

  /**
   * Generates IR with custom diagnostic collection.
   *
   * <p>Unlike {@link #generate(SymbolTable, NonTerminalNode)}, this method
   * collects diagnostics into the provided collector instead of throwing
   * immediately. This allows callers to inspect all errors.
   *
   * @param globalScope the global symbol table
   * @param semanticTree the semantic tree
   * @param collector the diagnostic collector
   * @return the generated IR program (may be invalid if errors occurred)
   */
  public static IrProgram generateWithDiagnostics(
      SymbolTable globalScope,
      NonTerminalNode semanticTree,
      DiagnosticCollector collector) {
    Objects.requireNonNull(globalScope, "globalScope must not be null");
    Objects.requireNonNull(semanticTree, "semanticTree must not be null");
    Objects.requireNonNull(collector, "collector must not be null");

    ProgramGenerator generator = new ProgramGenerator(globalScope);
    IrProgram program = generator.generate(semanticTree);

    // Verify and collect diagnostics
    IrVerifier.verifyWithDiagnostics(program, collector);

    return program;
  }

  /**
   * Pretty-prints an IR program to a string.
   *
   * <p>The output follows the IR grammar exactly and is deterministic.
   * Repeated calls with the same program produce identical output.
   *
   * @param program the IR program to print
   * @return the pretty-printed IR string
   * @throws NullPointerException if program is null
   */
  public static String print(IrProgram program) {
    Objects.requireNonNull(program, "program must not be null");
    return IrPrettyPrinter.print(program);
  }

  /**
   * Verifies an IR program for correctness.
   *
   * <p>Validates invariants including:
   * <ul>
   *   <li>Every block ends with exactly one terminator</li>
   *   <li>Temps are defined before use within blocks</li>
   *   <li>Types match for operations</li>
   *   <li>Branch targets reference valid labels</li>
   *   <li>Store/load address types are correct</li>
   * </ul>
   *
   * @param program the IR program to verify
   * @throws NullPointerException if program is null
   * @throws IrCompilationException if verification fails
   */
  public static void verify(IrProgram program) {
    Objects.requireNonNull(program, "program must not be null");
    IrVerifier.verify(program);
  }
}
