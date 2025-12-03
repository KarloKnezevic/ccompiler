/**
 * FRISC assembly code emission and formatting.
 * 
 * <p>This package contains classes responsible for emitting well-formatted FRISC
 * assembly code to output streams or files. The emitter handles:
 * <ul>
 *   <li>Instruction formatting with proper indentation</li>
 *   <li>Comment alignment</li>
 *   <li>Label placement</li>
 *   <li>Code buffering before final output</li>
 *   <li>Large immediate constant construction (20-bit signed immediate handling)</li>
 * </ul>
 * 
 * <p><b>Key Classes:</b>
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.emitter.FriscEmitter} - Main emitter for FRISC instructions</li>
 * </ul>
 * 
 * <p><b>FRISC Formatting Conventions:</b>
 * <ul>
 *   <li>Labels start at column 0</li>
 *   <li>Instructions indented with 8 spaces</li>
 *   <li>Comments aligned to column 32</li>
 *   <li>Immediate values use {@code %D} prefix for decimal</li>
 *   <li>Large immediates (>20 bits) constructed using SHL/ADD pattern</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.emitter;

