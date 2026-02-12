package hr.fer.ppj.opt.api;

import java.util.Objects;

/**
 * Options controlling IR optimization execution.
 */
public record OptimizationOptions(
    OptimizationLevel level,
    int maxIterations,
    boolean validateAfterEachPass) {

  public static final int DEFAULT_MAX_ITERATIONS = 5;

  public static final OptimizationOptions O0 =
      new OptimizationOptions(OptimizationLevel.O0, DEFAULT_MAX_ITERATIONS, false);

  public static final OptimizationOptions O1 =
      new OptimizationOptions(OptimizationLevel.O1, DEFAULT_MAX_ITERATIONS, false);

  public OptimizationOptions {
    Objects.requireNonNull(level, "level must not be null");
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("maxIterations must be positive");
    }
  }

  public static OptimizationOptions defaults() {
    return O1;
  }
}
