package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.List;
import java.util.Objects;

/**
 * Sealed interface for IR instructions.
 *
 * <p>Instructions include assignments, stores, and void calls.
 * Terminators are separate (br, jmp, ret).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface IrInstruction {
  /**
   * Assignment: t0 = add t1, t2 : int32
   */
  record IrAssignInstr(IrTemp dest, IrRhs rhs) implements IrInstruction {
    public IrAssignInstr {
      Objects.requireNonNull(dest, "dest must not be null");
      Objects.requireNonNull(rhs, "rhs must not be null");
    }
  }

  /**
   * Store: store addr, value : T
   */
  record IrStoreInstr(IrValue addr, IrValue value, IrType storeType)
      implements IrInstruction {
    public IrStoreInstr {
      Objects.requireNonNull(addr, "addr must not be null");
      Objects.requireNonNull(value, "value must not be null");
      Objects.requireNonNull(storeType, "storeType must not be null");
    }
  }

  /**
   * Void call: call func:name (t0, t1) : void
   */
  record IrVoidCallInstr(String funcName, List<IrValue> args) implements IrInstruction {
    public IrVoidCallInstr {
      Objects.requireNonNull(funcName, "funcName must not be null");
      Objects.requireNonNull(args, "args must not be null");
    }
  }
}

