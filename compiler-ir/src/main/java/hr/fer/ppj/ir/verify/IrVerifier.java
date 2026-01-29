package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrProgram;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verifies IR program invariants.
 *
 * <p>Checks:
 * <ul>
 *   <li>Every block ends with exactly one terminator</li>
 *   <li>Temps are defined before use (within function)</li>
 *   <li>Types match for operations</li>
 *   <li>Address/value correctness (load/store use addresses)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrVerifier {

  private final VerificationContext context;
  private final InstructionVerifier instructionVerifier;
  private final TerminatorVerifier terminatorVerifier;
  private final ValueVerifier valueVerifier;

  private IrVerifier() {
    this.context = new VerificationContext();
    this.valueVerifier = new ValueVerifier(context);
    this.instructionVerifier = new InstructionVerifier(context);
    this.terminatorVerifier = new TerminatorVerifier(context, valueVerifier);
  }

  /**
   * Verifies an IR program and throws an exception if errors are found.
   *
   * @param program the program to verify
   * @throws IrVerificationException if verification fails
   */
  public static void verify(IrProgram program) {
    IrVerifier verifier = new IrVerifier();
    verifier.verifyProgram(program);
    if (verifier.context.hasErrors()) {
      throw new IrVerificationException(
          String.join("\n", verifier.context.getErrors()));
    }
  }

  private void verifyProgram(IrProgram program) {
    for (IrFunction function : program.functions()) {
      verifyFunction(function);
    }
  }

  private void verifyFunction(IrFunction function) {
    Map<String, IrBlock> blocks = new HashMap<>();

    // Index blocks by label
    for (IrBlock block : function.blocks()) {
      if (blocks.containsKey(block.label())) {
        context.addError(function.name(), "Block", "Duplicate block label: " + block.label());
      }
      blocks.put(block.label(), block);
    }

    // Verify each block independently (per-block temp scoping)
    for (IrBlock block : function.blocks()) {
      verifyBlock(function.name(), block, blocks);
    }
  }

  /**
   * Verifies a block with per-block temp scoping.
   *
   * <p>Temps must be defined before use within the same block.
   * Cross-block temp usage is illegal (IR has no phi nodes).
   */
  private void verifyBlock(
      String functionName,
      IrBlock block,
      Map<String, IrBlock> allBlocks) {
    // Each block has its own set of defined temps
    Set<Integer> definedTemps = new HashSet<>();

    int instrIndex = 0;
    for (hr.fer.ppj.ir.model.IrInstruction instr : block.instructions()) {
      instructionVerifier.verifyInstruction(functionName, block.label(), instrIndex, instr, definedTemps);
      instrIndex++;
    }

    hr.fer.ppj.ir.model.IrTerminator terminator = block.terminator();
    if (terminator == null) {
      context.addError(
          functionName, block.label(), "Block must end with exactly one terminator (br/jmp/ret)");
      return;
    }
    terminatorVerifier.verifyTerminator(functionName, block.label(), terminator, allBlocks, definedTemps);
  }

  /**
   * Exception thrown when IR verification fails.
   */
  public static final class IrVerificationException extends RuntimeException {
    public IrVerificationException(String message) {
      super(message);
    }
  }
}

