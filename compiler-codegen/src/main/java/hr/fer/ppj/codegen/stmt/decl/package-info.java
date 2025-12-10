/**
 * Declaration-related code generation utilities.
 *
 * <p>This package contains specialized generators and utilities for handling local variable
 * declarations, including array initialization and size extraction.
 *
 * <p><b>Key Responsibilities:</b>
 *
 * <ul>
 *   <li><b>Array Initialization:</b> Generate code for initializing arrays with initializer lists
 *   <li><b>Array Size Extraction:</b> Extract array dimensions from declarator syntax
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.stmt.decl.ArrayInitializerGenerator} - Array initializer code
 *       generation
 *   <li>{@link hr.fer.ppj.codegen.stmt.decl.ArraySizeExtractor} - Array size extraction from parse
 *       tree
 * </ul>
 *
 * <p>These classes are used by {@link hr.fer.ppj.codegen.stmt.LocalDeclarationGenerator} to handle
 * complex declaration scenarios like array initialization.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.stmt.decl;
