/**
 * Utility classes for code generation.
 * 
 * <p>This package contains reusable utility classes used throughout the
 * code generation process, including:
 * <ul>
 *   <li>Label generation for unique identifiers</li>
 *   <li>String formatting helpers</li>
 *   <li>Number conversion utilities</li>
 *   <li>Validation helpers</li>
 * </ul>
 * 
 * <p><b>Key Classes:</b>
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.util.LabelGenerator} - Unique label generation</li>
 * </ul>
 * 
 * <p><b>Label Naming Conventions:</b>
 * <ul>
 *   <li>Functions: {@code F_<FUNCTION_NAME>}</li>
 *   <li>Global variables: {@code G_<VARIABLE_NAME>}</li>
 *   <li>Labels: {@code L<number>} (for control flow)</li>
 *   <li>String literals: {@code STR<number>}</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.util;

