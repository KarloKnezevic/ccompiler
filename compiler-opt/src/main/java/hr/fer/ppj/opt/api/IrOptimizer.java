package hr.fer.ppj.opt.api;

import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassPipeline;
import hr.fer.ppj.opt.rules.arith.Int32ArithmeticPass;
import hr.fer.ppj.opt.rules.controlflow.ControlFlowSimplificationPass;
import hr.fer.ppj.opt.rules.controlflow.UnreachableBlockEliminationPass;
import hr.fer.ppj.opt.rules.flow.GlobalValuePropagationPass;
import hr.fer.ppj.opt.rules.loop.InductionStrengthReductionPass;
import hr.fer.ppj.opt.rules.range.ValueRangeSimplificationPass;
import hr.fer.ppj.opt.rules.shift.Int32ShiftPass;
import hr.fer.ppj.opt.rules.temps.CopyPropagationPass;
import hr.fer.ppj.opt.rules.temps.DeadTempEliminationPass;
import hr.fer.ppj.opt.validation.IrOptimizationValidator;
import java.util.List;
import java.util.Objects;

/**
 * Public entrypoint for the {@code compiler-opt} module.
 */
public final class IrOptimizer {

  private final IrOptimizationValidator validator;

  public IrOptimizer() {
    this(new IrOptimizationValidator());
  }

  IrOptimizer(IrOptimizationValidator validator) {
    this.validator = Objects.requireNonNull(validator, "validator must not be null");
  }

  /**
   * Optimizes the input IR program according to the provided options.
   *
   * @param program input IR program
   * @param options optimization options
   * @return optimized IR program
   */
  public IrProgram optimize(IrProgram program, OptimizationOptions options) {
    Objects.requireNonNull(program, "program must not be null");
    OptimizationOptions resolvedOptions =
        options == null ? OptimizationOptions.defaults() : options;

    validator.validate(program);
    if (resolvedOptions.level() == OptimizationLevel.O0) {
      return program;
    }

    PassPipeline pipeline = new PassPipeline(List.of(
        new Int32ArithmeticPass(),
        new Int32ShiftPass(),
        new GlobalValuePropagationPass(),
        new ValueRangeSimplificationPass(),
        new CopyPropagationPass(),
        new DeadTempEliminationPass(),
        new ControlFlowSimplificationPass(),
        new UnreachableBlockEliminationPass(),
        new InductionStrengthReductionPass(),
        new DeadTempEliminationPass(),
        new ControlFlowSimplificationPass(),
        new UnreachableBlockEliminationPass()));

    IrProgram optimized = pipeline.run(program, new PassContext(resolvedOptions, validator));
    validator.validate(optimized);
    return optimized;
  }
}
