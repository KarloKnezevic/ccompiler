package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;

/**
 * Sealed interface for IR values (Temp or Const).
 *
 * <p>Values are used in RHS expressions and as operands to instructions.
 * According to the grammar, Value is either Temp or Const (not SymbolRef).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface IrValue permits IrTemp, IrConst {
  IrType type();
}

