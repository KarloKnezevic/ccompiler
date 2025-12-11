/**
 * Utility classes for code generation.
 *
 * <p>This package contains reusable utility classes used throughout the code generation process,
 * including:
 *
 * <ul>
 *   <li>Label generation for unique identifiers
 *   <li>String formatting helpers
 *   <li>Number conversion utilities
 *   <li>Validation helpers
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.util.LabelGenerator} - Unique label generation
 * </ul>
 *
 * <p><b>Label Naming Conventions:</b>
 *
 * <ul>
 *   <li>Functions: {@code F_<FUNCTION_NAME>}
 *   <li>Global variables: {@code G_<VARIABLE_NAME>}
 *   <li>Labels: {@code L<number>} (for control flow)
 *   <li>String literals: {@code STR<number>}
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.util;
