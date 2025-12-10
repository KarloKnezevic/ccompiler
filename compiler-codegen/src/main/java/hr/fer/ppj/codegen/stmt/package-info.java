/**
 * Statement code generation (control flow).
 *
 * <p>This package handles the generation of FRISC code for all statement types, including control
 * flow constructs.
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <ul>
 *   <li>{@code <naredba>} - General statement wrapper
 *   <li>{@code <slozena_naredba>} - Compound statements (blocks)
 *   <li>{@code <izraz_naredba>} - Expression statements
 *   <li>{@code <naredba_grananja>} - If-else statements
 *   <li>{@code <naredba_petlje>} - Loop statements (while, for)
 *   <li>{@code <naredba_skoka>} - Jump statements (return, break, continue)
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.stmt.StatementCodeGenerator} - Main orchestrator for statement
 *       code generation
 *   <li>{@link hr.fer.ppj.codegen.stmt.BranchingStatementGenerator} - If-else statement generation
 *   <li>{@link hr.fer.ppj.codegen.stmt.LoopStatementGenerator} - While and for loop generation
 *   <li>{@link hr.fer.ppj.codegen.stmt.JumpStatementGenerator} - Return, break, continue statement
 *       generation
 *   <li>{@link hr.fer.ppj.codegen.stmt.LocalDeclarationGenerator} - Local variable declaration
 *       generation
 * </ul>
 *
 * <p><b>Control Flow Implementation:</b>
 *
 * <ul>
 *   <li>Labels generated for control flow targets
 *   <li>Conditional jumps (JP_EQ, JP_NE, JP_LT, etc.)
 *   <li>Unconditional jumps (JP) for break/continue/return
 *   <li>Nested scopes handled via ActivationRecord
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.stmt;
