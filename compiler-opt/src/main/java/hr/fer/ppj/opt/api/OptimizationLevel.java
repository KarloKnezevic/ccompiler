package hr.fer.ppj.opt.api;

/**
 * Supported optimization levels for IR-to-IR optimization.
 */
public enum OptimizationLevel {
  /**
   * No optimization.
   */
  O0,

  /**
   * Basic peephole and CFG simplifications.
   */
  O1
}
