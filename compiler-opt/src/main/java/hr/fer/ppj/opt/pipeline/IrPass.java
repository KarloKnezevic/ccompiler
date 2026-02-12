package hr.fer.ppj.opt.pipeline;

import hr.fer.ppj.ir.model.IrProgram;

/**
 * A single optimization pass over IR.
 */
public interface IrPass {

  /**
   * @return deterministic pass name used for diagnostics
   */
  String name();

  /**
   * Runs the pass.
   */
  PassResult run(IrProgram program, PassContext context);
}
