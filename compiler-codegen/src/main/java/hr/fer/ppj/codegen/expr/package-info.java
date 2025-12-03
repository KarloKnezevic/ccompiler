/**
 * Expression code generation.
 * 
 * <p>This package handles the generation of FRISC code for all expression types,
 * organized hierarchically according to C expression precedence.
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <ul>
 *   <li>{@code <izraz>} - General expression</li>
 *   <li>{@code <izraz_pridruzivanja>} - Assignment expressions</li>
 *   <li>{@code <log_ili_izraz>} - Logical OR expressions</li>
 *   <li>{@code <log_i_izraz>} - Logical AND expressions</li>
 *   <li>{@code <bin_ili_izraz>} - Bitwise OR expressions</li>
 *   <li>{@code <bin_xili_izraz>} - Bitwise XOR expressions</li>
 *   <li>{@code <bin_i_izraz>} - Bitwise AND expressions</li>
 *   <li>{@code <jednakosni_izraz>} - Equality expressions (==, !=)</li>
 *   <li>{@code <odnosni_izraz>} - Relational expressions (<, >, <=, >=)</li>
 *   <li>{@code <aditivni_izraz>} - Additive expressions (+, -)</li>
 *   <li>{@code <multiplikativni_izraz>} - Multiplicative expressions (*, /, %)</li>
 *   <li>{@code <cast_izraz>} - Cast expressions</li>
 *   <li>{@code <unarni_izraz>} - Unary expressions (+, -, !)</li>
 *   <li>{@code <postfiks_izraz>} - Postfix expressions (function calls, array indexing)</li>
 *   <li>{@code <primarni_izraz>} - Primary expressions (identifiers, constants)</li>
 * </ul>
 * 
 * <p><b>Subpackages:</b>
 * <ul>
 *   <li>{@code binary} - Binary arithmetic and relational operations</li>
 *   <li>{@code logical} - Logical operations (&&, ||) with short-circuit evaluation</li>
 *   <li>{@code unary} - Unary operations (+, -, !, casts)</li>
 *   <li>{@code assignment} - Assignment operations (=, +=, -=, ++, --)</li>
 *   <li>{@code array} - Array indexing and element access</li>
 *   <li>{@code call} - Function call argument generation</li>
 *   <li>{@code primary} - Primary expressions (identifiers, constants, parentheses)</li>
 * </ul>
 * 
 * <p><b>Key Classes:</b>
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.expr.ExpressionCodeGenerator} - Expression orchestrator</li>
 * </ul>
 * 
 * <p><b>Expression Evaluation:</b>
 * <ul>
 *   <li>Results typically left in R0</li>
 *   <li>Left operands saved on stack before evaluating right operands</li>
 *   <li>Short-circuit evaluation for && and ||</li>
 *   <li>Type conversions handled according to C semantics</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.expr;

