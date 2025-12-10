/**
 * Domain models for code generation.
 *
 * <p>This package contains value objects and domain models that represent code generation concepts
 * such as:
 *
 * <ul>
 *   <li>Stack frame layouts and activation records
 *   <li>Variable offsets and addresses
 *   <li>Function metadata
 *   <li>Symbol information for code generation
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.model.ActivationRecord} - Stack frame management
 * </ul>
 *
 * <p><b>Stack Frame Layout:</b>
 *
 * <ul>
 *   <li>Parameters: positive offsets from R5 (e.g., (R5+8), (R5+12))
 *   <li>Return address: (R5+4)
 *   <li>Old frame pointer: (R5+0)
 *   <li>Local variables: negative offsets from R5 (e.g., (R5-4), (R5-8))
 *   <li>Stack grows downward (R7 decreases for allocation)
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.model;
