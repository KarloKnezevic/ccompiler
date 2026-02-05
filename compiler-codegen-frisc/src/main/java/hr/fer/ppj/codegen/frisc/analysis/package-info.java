/**
 * Lightweight analysis results and data carriers used during FRISC lowering.
 *
 * <p>Types here are simple immutable containers returned by analysis passes
 * (e.g., temp usage or scratch allocation). They intentionally avoid any
 * dependency on emission to keep analysis separate from code generation.
 */
package hr.fer.ppj.codegen.frisc.analysis;
