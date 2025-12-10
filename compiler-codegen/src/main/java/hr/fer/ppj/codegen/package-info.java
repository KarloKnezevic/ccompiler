/**
 * Main package for FRISC assembly code generation from C programs.
 *
 * <p>This package contains the core code generation infrastructure that transforms semantically
 * validated C programs (represented as annotated ASTs) into executable FRISC assembly code.
 *
 * <p>The code generation process follows a visitor-like pattern, traversing the parse tree and
 * emitting FRISC instructions according to the PPJ-C to FRISC mapping specification.
 *
 * <p><b>Key Components:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.CodeGenerator} - Main orchestrator for code generation
 *   <li>{@link hr.fer.ppj.codegen.CodeGenContext} - Shared state during code generation
 *   <li>{@link hr.fer.ppj.codegen.CodeGenerationException} - Exception for codegen errors
 * </ul>
 *
 * <p><b>Package Organization:</b>
 *
 * <ul>
 *   <li>{@code emitter} - FRISC code emission and formatting
 *   <li>{@code model} - Domain models (stack frames, activation records)
 *   <li>{@code util} - Utility classes (label generation, etc.)
 *   <li>{@code frisc} - FRISC-specific helpers (F_MUL, F_DIV)
 *   <li>{@code func} - Function definition and call generation
 *   <li>{@code stmt} - Statement generation (control flow)
 *   <li>{@code expr} - Expression generation (arithmetic, logical, etc.)
 *   <li>{@code global} - Global variable declaration generation
 * </ul>
 *
 * <p><b>FRISC Calling Convention:</b>
 *
 * <ul>
 *   <li>R7 - Stack Pointer (SP)
 *   <li>R5 - Frame Pointer (FP)
 *   <li>R6 - Return Value Register
 *   <li>Arguments passed on stack (right-to-left)
 *   <li>Caller cleans up arguments
 *   <li>Local variables allocated on stack
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen;
