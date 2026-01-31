package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.diagnostic.DiagnosticCollector;
import hr.fer.ppj.ir.diagnostic.IrCompilationException;
import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrSlot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verifies IR program invariants as defined by the IR grammar.
 *
 * <p>This verifier ensures the IR program is well-formed according to
 * {@code config/ir_definition.txt}. It validates:
 *
 * <h3>Program Structure (Grammar: Program)</h3>
 * <ul>
 *   <li>Program contains valid top-level declarations</li>
 *   <li>No duplicate function definitions</li>
 *   <li>No duplicate struct definitions</li>
 * </ul>
 *
 * <h3>Function Structure (Grammar: FuncDef)</h3>
 * <ul>
 *   <li>Valid frame declaration (FrameDecl)</li>
 *   <li>Valid slots declaration (SlotsDecl)</li>
 *   <li>At least one block exists</li>
 * </ul>
 *
 * <h3>Block Structure (Grammar: Block)</h3>
 * <ul>
 *   <li>Unique labels within function</li>
 *   <li>Every block ends with exactly one terminator (br/jmp/ret)</li>
 *   <li>Branch targets reference valid labels</li>
 * </ul>
 *
 * <h3>Instruction Correctness (Grammar: Instr)</h3>
 * <ul>
 *   <li>Type correctness for all operations</li>
 *   <li>Def-before-use for temporaries within blocks</li>
 *   <li>Store addresses must be pointer types</li>
 * </ul>
 *
 * <h3>Slot Correctness (Grammar: SlotsDecl, SlotEntry)</h3>
 * <ul>
 *   <li>No duplicate slot names within same kind</li>
 *   <li>No overlapping offsets within same kind (params and locals are separate)</li>
 *   <li>Valid slot types</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see hr.fer.ppj.ir.model.IrProgram
 */
public final class IrVerifier {

  private final VerificationContext context;
  private final InstructionVerifier instructionVerifier;
  private final TerminatorVerifier terminatorVerifier;
  private final ValueVerifier valueVerifier;
  private final SlotVerifier slotVerifier;

  private IrVerifier(VerificationContext context) {
    this.context = context;
    this.valueVerifier = new ValueVerifier(context);
    this.instructionVerifier = new InstructionVerifier(context);
    this.terminatorVerifier = new TerminatorVerifier(context, valueVerifier);
    this.slotVerifier = new SlotVerifier(context);
  }

  /**
   * Verifies an IR program and throws an exception if errors are found.
   *
   * <p>This method performs comprehensive validation of the IR program
   * structure, types, and invariants. Any detected error causes an
   * exception to be thrown with detailed diagnostic information.
   *
   * @param program the program to verify
   * @throws IrCompilationException if verification fails
   * @throws NullPointerException if program is null
   */
  public static void verify(IrProgram program) {
    if (program == null) {
      throw new IrCompilationException("Program must not be null");
    }
    DiagnosticCollector collector = new DiagnosticCollector();
    VerificationContext ctx = new VerificationContext(collector);
    IrVerifier verifier = new IrVerifier(ctx);
    verifier.verifyProgram(program);
    if (collector.hasErrors()) {
      throw new IrCompilationException(collector.getErrors());
    }
  }

  /**
   * Verifies an IR program and returns diagnostics without throwing.
   *
   * @param program the program to verify
   * @param collector the collector to receive diagnostics
   * @return true if verification passed (no errors)
   */
  public static boolean verifyWithDiagnostics(
      IrProgram program, DiagnosticCollector collector) {
    if (program == null) {
      collector.reportGlobalError("Program must not be null");
      return false;
    }
    VerificationContext ctx = new VerificationContext(collector);
    IrVerifier verifier = new IrVerifier(ctx);
    verifier.verifyProgram(program);
    return !collector.hasErrors();
  }

  private void verifyProgram(IrProgram program) {
    verifyNoDuplicateFunctions(program);
    for (IrFunction function : program.functions()) {
      verifyFunction(function);
    }
  }

  private void verifyNoDuplicateFunctions(IrProgram program) {
    Set<String> seen = new HashSet<>();
    for (IrFunction func : program.functions()) {
      if (!seen.add(func.name())) {
        context.addGlobalError("Duplicate function definition: " + func.name());
      }
    }
  }

  private void verifyFunction(IrFunction function) {
    if (function.blocks().isEmpty()) {
      context.addFunctionError(function.name(), "Function must have at least one block");
    }

    slotVerifier.verifySlots(function.name(), function.slots());
    verifyBlocks(function);
  }

  private void verifyBlocks(IrFunction function) {
    Map<String, IrBlock> blocks = new HashMap<>();

    for (IrBlock block : function.blocks()) {
      if (blocks.containsKey(block.label())) {
        context.addFunctionError(function.name(),
            "Duplicate block label: " + block.label());
      }
      blocks.put(block.label(), block);
    }

    for (IrBlock block : function.blocks()) {
      verifyBlock(function.name(), block, blocks);
    }
  }

  private void verifyBlock(
      String functionName, IrBlock block, Map<String, IrBlock> allBlocks) {
    Set<Integer> definedTemps = new HashSet<>();

    int instrIndex = 0;
    for (hr.fer.ppj.ir.model.IrInstruction instr : block.instructions()) {
      instructionVerifier.verifyInstruction(
          functionName, block.label(), instrIndex, instr, definedTemps);
      instrIndex++;
    }

    hr.fer.ppj.ir.model.IrTerminator terminator = block.terminator();
    if (terminator == null) {
      context.addError(functionName, block.label(),
          "Block must end with exactly one terminator (br/jmp/ret)");
      return;
    }
    terminatorVerifier.verifyTerminator(
        functionName, block.label(), terminator, allBlocks, definedTemps);
  }

  /**
   * Exception thrown when IR verification fails.
   *
   * @deprecated Use {@link IrCompilationException} instead
   */
  @Deprecated(forRemoval = true)
  public static final class IrVerificationException extends RuntimeException {
    public IrVerificationException(String message) {
      super(message);
    }
  }
}
