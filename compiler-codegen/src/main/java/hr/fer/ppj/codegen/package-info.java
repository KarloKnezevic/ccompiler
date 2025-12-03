/**
 * Main package for FRISC assembly code generation from C programs.
 * 
 * <p>This package contains the core code generation infrastructure that transforms
 * semantically validated C programs (represented as annotated ASTs) into executable
 * FRISC assembly code.
 * 
 * <p>The code generation process follows a visitor-like pattern, traversing the
 * parse tree and emitting FRISC instructions according to the PPJ-C to FRISC
 * mapping specification.
 * 
 * <p><b>Key Components:</b>
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.CodeGenerator} - Main orchestrator for code generation</li>
 *   <li>{@link hr.fer.ppj.codegen.CodeGenContext} - Shared state during code generation</li>
 *   <li>{@link hr.fer.ppj.codegen.CodeGenerationException} - Exception for codegen errors</li>
 * </ul>
 * 
 * <p><b>Package Organization:</b>
 * <ul>
 *   <li>{@code emitter} - FRISC code emission and formatting</li>
 *   <li>{@code model} - Domain models (stack frames, activation records)</li>
 *   <li>{@code util} - Utility classes (label generation, etc.)</li>
 *   <li>{@code frisc} - FRISC-specific helpers (F_MUL, F_DIV)</li>
 *   <li>{@code func} - Function definition and call generation</li>
 *   <li>{@code stmt} - Statement generation (control flow)</li>
 *   <li>{@code expr} - Expression generation (arithmetic, logical, etc.)</li>
 *   <li>{@code global} - Global variable declaration generation</li>
 * </ul>
 * 
 * <p><b>FRISC Calling Convention:</b>
 * <ul>
 *   <li>R7 - Stack Pointer (SP)</li>
 *   <li>R5 - Frame Pointer (FP)</li>
 *   <li>R6 - Return Value Register</li>
 *   <li>Arguments passed on stack (right-to-left)</li>
 *   <li>Caller cleans up arguments</li>
 *   <li>Local variables allocated on stack</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen;

