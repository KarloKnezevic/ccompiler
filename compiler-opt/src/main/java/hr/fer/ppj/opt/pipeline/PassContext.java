package hr.fer.ppj.opt.pipeline;

import hr.fer.ppj.opt.api.OptimizationOptions;
import hr.fer.ppj.opt.validation.IrOptimizationValidator;
import java.util.Objects;

/**
 * Shared pass execution context.
 */
public record PassContext(
    OptimizationOptions options,
    IrOptimizationValidator validator) {

  public PassContext {
    Objects.requireNonNull(options, "options must not be null");
    Objects.requireNonNull(validator, "validator must not be null");
  }
}
