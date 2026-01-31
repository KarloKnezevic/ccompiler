package hr.fer.ppj.ir.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a basic block in the IR control flow graph.
 *
 * <p>This record corresponds to the Block production in the IR grammar
 * ({@code config/ir_definition.txt}):
 *
 * <pre>
 * Block
 *   ::= Label ":" NL
 *       { Instr NL }
 *       Terminator NL ;
 *
 * Label
 *   ::= Ident ;        ; e.g., L0, L1, loop_body, etc.
 * </pre>
 *
 * <h3>Block Invariants</h3>
 * <ul>
 *   <li>Every block must have exactly one terminator (br/jmp/ret)</li>
 *   <li>Instructions execute sequentially until the terminator</li>
 *   <li>No instructions may follow the terminator</li>
 *   <li>Labels must be unique within a function</li>
 * </ul>
 *
 * <h3>Def-Before-Use</h3>
 * <p>Temporaries must be defined before use within the same block.
 * Cross-block temporary usage is prohibited (no phi nodes).
 *
 * @param label the block label (e.g., "L0", "loop_body")
 * @param instructions the list of non-terminating instructions
 * @param terminator the block terminator (br, jmp, or ret)
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see IrInstruction
 * @see IrTerminator
 */
public record IrBlock(String label, List<IrInstruction> instructions, IrTerminator terminator) {

  /**
   * Creates an IR block with validation.
   *
   * @throws NullPointerException if any argument is null
   */
  public IrBlock {
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(instructions, "instructions must not be null");
    Objects.requireNonNull(terminator, "terminator must not be null");
    instructions = List.copyOf(instructions);
  }

  /**
   * Creates a new block builder.
   *
   * @param label the block label
   * @return a new builder instance
   */
  public static Builder builder(String label) {
    return new Builder(label);
  }

  /**
   * Builder for constructing blocks incrementally.
   *
   * <p>Usage:
   * <pre>{@code
   * IrBlock block = IrBlock.builder("L0")
   *     .addInstruction(assignInstr)
   *     .addInstruction(storeInstr)
   *     .setTerminator(new IrTerminator.IrRetTerm(retValue))
   *     .build();
   * }</pre>
   */
  public static final class Builder {
    private final String label;
    private final List<IrInstruction> instructions = new ArrayList<>();
    private IrTerminator terminator;

    private Builder(String label) {
      this.label = Objects.requireNonNull(label, "label must not be null");
    }

    /**
     * Adds an instruction to the block.
     *
     * @param instruction the instruction to add
     * @return this builder
     * @throws NullPointerException if instruction is null
     */
    public Builder addInstruction(IrInstruction instruction) {
      instructions.add(Objects.requireNonNull(instruction, "instruction must not be null"));
      return this;
    }

    /**
     * Sets the block terminator.
     *
     * @param terminator the terminator
     * @return this builder
     * @throws NullPointerException if terminator is null
     */
    public Builder setTerminator(IrTerminator terminator) {
      this.terminator = Objects.requireNonNull(terminator, "terminator must not be null");
      return this;
    }

    /**
     * Builds the immutable block.
     *
     * @return the constructed block
     * @throws IllegalStateException if no terminator was set
     */
    public IrBlock build() {
      if (terminator == null) {
        throw new IllegalStateException("Block must have a terminator");
      }
      return new IrBlock(label, instructions, terminator);
    }
  }
}
