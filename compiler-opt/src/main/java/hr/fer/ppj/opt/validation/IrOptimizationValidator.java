package hr.fer.ppj.opt.validation;

import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrProgram;
import java.util.Objects;

/**
 * Validation adapter used by optimizer passes.
 */
public final class IrOptimizationValidator {

  public void validate(IrProgram program) {
    Objects.requireNonNull(program, "program must not be null");
    IrPipeline.verify(program);
  }
}
