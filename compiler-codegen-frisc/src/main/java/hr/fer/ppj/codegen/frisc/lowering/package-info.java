/**
 * IR lowering and instruction selection for the FRISC backend.
 *
 * <p>Classes in this package translate the typed IR into concrete FRISC
 * instruction sequences. The logic is split by responsibility: statements,
 * expressions, addresses, and frame access helpers.
 */
package hr.fer.ppj.codegen.frisc.lowering;
