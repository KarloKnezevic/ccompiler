package hr.fer.ppj.codegen.frisc.helpers;

/**
 * Generates deterministic helper-local labels.
 */
final class HelperLabeler {
  private int counter;

  String next(String prefix) {
    counter += 1;
    return prefix + "_" + counter;
  }
}
