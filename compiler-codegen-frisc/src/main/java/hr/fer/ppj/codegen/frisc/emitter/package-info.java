/**
 * Assembly emission utilities for FRISC.
 *
 * <p>Classes in this package are responsible for formatting and writing FRISC
 * assembly text. Emission is intentionally side-effect free beyond buffering
 * output lines, and it must preserve the exact formatting expected by the
 * simulator and golden tests.
 */
package hr.fer.ppj.codegen.frisc.emitter;
