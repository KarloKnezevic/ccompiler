package hr.fer.ppj.opt.pipeline;

import hr.fer.ppj.ir.model.IrProgram;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic fixpoint pipeline for optimization passes.
 */
public final class PassPipeline {

  private final List<IrPass> passes;

  public PassPipeline(List<IrPass> passes) {
    Objects.requireNonNull(passes, "passes must not be null");
    this.passes = List.copyOf(passes);
  }

  public IrProgram run(IrProgram input, PassContext context) {
    Objects.requireNonNull(input, "input must not be null");
    Objects.requireNonNull(context, "context must not be null");

    IrProgram current = input;

    for (int iteration = 0; iteration < context.options().maxIterations(); iteration++) {
      boolean changedInIteration = false;
      for (IrPass pass : passes) {
        PassResult result = pass.run(current, context);
        current = result.program();
        changedInIteration |= result.changed();

        if (context.options().validateAfterEachPass()) {
          context.validator().validate(current);
        }
      }
      if (!changedInIteration) {
        break;
      }
    }

    return current;
  }
}
