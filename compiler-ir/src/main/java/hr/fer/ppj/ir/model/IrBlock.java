package hr.fer.ppj.ir.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * IR block: a labeled sequence of instructions ending with a terminator.
 *
 * <p>Blocks are the basic unit of control flow in the IR.
 * Each block has a label and contains zero or more instructions
 * followed by exactly one terminator.
 *
 * @param label the block label (e.g., "L0")
 * @param instructions the list of instructions (can be empty)
 * @param terminator the block terminator (required)
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrBlock(String label, List<IrInstruction> instructions, IrTerminator terminator) {

  public IrBlock {
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(instructions, "instructions must not be null");
    Objects.requireNonNull(terminator, "terminator must not be null");
    instructions = List.copyOf(instructions);
  }

  /**
   * Creates a new block builder.
   */
  public static Builder builder(String label) {
    return new Builder(label);
  }

  /**
   * Builder for constructing blocks incrementally.
   */
  public static final class Builder {
    private final String label;
    private final List<IrInstruction> instructions = new ArrayList<>();
    private IrTerminator terminator;

    private Builder(String label) {
      this.label = Objects.requireNonNull(label, "label must not be null");
    }

    public Builder addInstruction(IrInstruction instruction) {
      instructions.add(Objects.requireNonNull(instruction, "instruction must not be null"));
      return this;
    }

    public Builder setTerminator(IrTerminator terminator) {
      this.terminator = Objects.requireNonNull(terminator, "terminator must not be null");
      return this;
    }

    public IrBlock build() {
      if (terminator == null) {
        throw new IllegalStateException("Block must have a terminator");
      }
      return new IrBlock(label, instructions, terminator);
    }
  }
}

