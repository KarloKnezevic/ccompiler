/**
 * Address generation utilities for L-value expressions.
 *
 * <p>This package contains utilities for computing addresses of L-values (left-hand sides of
 * assignments) in FRISC assembly code generation. It provides focused, single-responsibility
 * classes for different aspects of address computation.
 *
 * <p><b>Key Responsibilities:</b>
 *
 * <ul>
 *   <li><b>Expression Unwrapping:</b> Unwrap expression layers to extract underlying L-values
 *   <li><b>Pattern Matching:</b> Identify field access and array indexing patterns in AST
 *   <li><b>Parameter Type Checking:</b> Determine if parameters need dereferencing (LOAD
 *       instruction)
 *   <li><b>Address Loading:</b> Generate FRISC code to load variable addresses into registers
 * </ul>
 *
 * <p><b>Design Pattern: Single Responsibility Principle</b>
 *
 * <p>Each class in this package has a single, well-defined responsibility:
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.utils.address.ExpressionUnwrapper} - Expression unwrapping logic
 *   <li>{@link hr.fer.ppj.codegen.utils.address.LValuePatternMatcher} - AST pattern matching
 *   <li>{@link hr.fer.ppj.codegen.utils.address.ParameterTypeChecker} - Parameter type analysis
 *   <li>{@link hr.fer.ppj.codegen.utils.address.VariableAddressLoader} - Address code generation
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <p>These utilities are used by {@link hr.fer.ppj.codegen.utils.LValueAddressGenerator} to compute
 * addresses for L-value expressions. They handle the complexity of:
 *
 * <ul>
 *   <li>Unwrapping nested expression layers
 *   <li>Identifying field access vs array indexing patterns
 *   <li>Determining when to emit LOAD instructions for parameters
 *   <li>Generating appropriate FRISC code for different address types
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.utils.address;
