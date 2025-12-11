/**
 * Function definition and call code generation.
 *
 * <p>This package handles the generation of FRISC code for function definitions and function calls,
 * implementing the standard FRISC calling convention.
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <ul>
 *   <li>{@code <definicija_funkcije>} - Function definitions
 *   <li>{@code <postfiks_izraz> ::= <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA} -
 *       Function calls
 *   <li>{@code <lista_parametara>} - Function parameter lists
 *   <li>{@code <lista_argumenata>} - Function argument lists
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.func.FunctionCodeGenerator} - Main orchestrator for function code
 *       generation
 *   <li>{@link hr.fer.ppj.codegen.func.FunctionInfoExtractor} - Extracts function names,
 *       parameters, and local variables from parse tree
 *   <li>{@link hr.fer.ppj.codegen.func.FunctionPrologueEpilogueGenerator} - Generates function
 *       prologue and epilogue code
 * </ul>
 *
 * <p><b>FRISC Calling Convention:</b>
 *
 * <ul>
 *   <li>Arguments pushed right-to-left
 *   <li>CALL instruction saves return address
 *   <li>Function prologue: PUSH R5, MOVE R7, R5
 *   <li>Function epilogue: POP R5, RET
 *   <li>Return value in R6
 *   <li>Caller cleans up arguments: ADD R7, %D N, R7
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.func;
