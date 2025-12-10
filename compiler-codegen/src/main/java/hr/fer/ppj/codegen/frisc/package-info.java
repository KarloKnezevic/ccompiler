/**
 * FRISC-specific code generation helpers.
 *
 * <p>This package contains FRISC architecture-specific code generation utilities, including:
 *
 * <ul>
 *   <li>Helper function implementations (F_MUL, F_DIV)
 *   <li>FRISC instruction sequence builders
 *   <li>FRISC-specific optimizations
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.frisc.HelperFunctionGenerator} - F_MUL and F_DIV generators
 * </ul>
 *
 * <p><b>FRISC Limitations:</b>
 *
 * <ul>
 *   <li>No native MUL instruction - uses F_MUL helper
 *   <li>No native DIV instruction - uses F_DIV helper
 *   <li>20-bit signed immediates (-524288 to 524287)
 *   <li>32-bit registers (signed arithmetic)
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.frisc;
