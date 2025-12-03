/**
 * Statement code generation (control flow).
 * 
 * <p>This package handles the generation of FRISC code for all statement types,
 * including control flow constructs.
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <ul>
 *   <li>{@code <naredba>} - General statement wrapper</li>
 *   <li>{@code <slozena_naredba>} - Compound statements (blocks)</li>
 *   <li>{@code <izraz_naredba>} - Expression statements</li>
 *   <li>{@code <naredba_grananja>} - If-else statements</li>
 *   <li>{@code <naredba_petlje>} - Loop statements (while, for)</li>
 *   <li>{@code <naredba_skoka>} - Jump statements (return, break, continue)</li>
 * </ul>
 * 
 * <p><b>Key Classes:</b>
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.stmt.StatementCodeGenerator} - Main orchestrator for statement code generation</li>
 *   <li>{@link hr.fer.ppj.codegen.stmt.BranchingStatementGenerator} - If-else statement generation</li>
 *   <li>{@link hr.fer.ppj.codegen.stmt.LoopStatementGenerator} - While and for loop generation</li>
 *   <li>{@link hr.fer.ppj.codegen.stmt.JumpStatementGenerator} - Return, break, continue statement generation</li>
 *   <li>{@link hr.fer.ppj.codegen.stmt.LocalDeclarationGenerator} - Local variable declaration generation</li>
 * </ul>
 * 
 * <p><b>Control Flow Implementation:</b>
 * <ul>
 *   <li>Labels generated for control flow targets</li>
 *   <li>Conditional jumps (JP_EQ, JP_NE, JP_LT, etc.)</li>
 *   <li>Unconditional jumps (JP) for break/continue/return</li>
 *   <li>Nested scopes handled via ActivationRecord</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.stmt;

