package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;

/**
 * Functional interface for emitting IR values into a target register.
 */
@FunctionalInterface
interface ValueEmitter {
  void emit(IrProgramModel.Value value, FunctionContext ctx, String targetReg);
}
