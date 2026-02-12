package hr.fer.ppj.opt.pipeline;

import hr.fer.ppj.ir.model.IrProgram;
import java.util.Objects;

/**
 * Result of executing an optimization pass.
 */
public record PassResult(IrProgram program, boolean changed) {

  public PassResult {
    Objects.requireNonNull(program, "program must not be null");
  }

  public static PassResult unchanged(IrProgram program) {
    return new PassResult(program, false);
  }

  public static PassResult changed(IrProgram program) {
    return new PassResult(program, true);
  }
}
