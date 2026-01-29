package hr.fer.ppj.ir.model;

import java.util.Objects;

/**
 * Sealed interface for IR terminators (br, jmp, ret).
 *
 * <p>Every block must end with exactly one terminator.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface IrTerminator {
  /**
   * Conditional branch: br t0, L1, L2
   */
  record IrBrTerm(IrValue condition, String trueLabel, String falseLabel)
      implements IrTerminator {
    public IrBrTerm {
      Objects.requireNonNull(condition, "condition must not be null");
      Objects.requireNonNull(trueLabel, "trueLabel must not be null");
      Objects.requireNonNull(falseLabel, "falseLabel must not be null");
    }
  }

  /**
   * Unconditional jump: jmp L1
   */
  record IrJmpTerm(String label) implements IrTerminator {
    public IrJmpTerm {
      Objects.requireNonNull(label, "label must not be null");
    }
  }

  /**
   * Return: ret or ret t0
   */
  record IrRetTerm(IrValue value) implements IrTerminator {
    // value is null for void return, non-null for value return
    public IrRetTerm {
      // value can be null for void return
    }
  }
}

