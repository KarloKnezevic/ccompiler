/**
 * Expression code generation.
 *
 * <p>This package handles the generation of FRISC code for all expression types, organized
 * hierarchically according to C expression precedence.
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <ul>
 *   <li>{@code <izraz>} - General expression
 *   <li>{@code <izraz_pridruzivanja>} - Assignment expressions
 *   <li>{@code <log_ili_izraz>} - Logical OR expressions
 *   <li>{@code <log_i_izraz>} - Logical AND expressions
 *   <li>{@code <bin_ili_izraz>} - Bitwise OR expressions
 *   <li>{@code <bin_xili_izraz>} - Bitwise XOR expressions
 *   <li>{@code <bin_i_izraz>} - Bitwise AND expressions
 *   <li>{@code <jednakosni_izraz>} - Equality expressions (==, !=)
 *   <li>{@code <odnosni_izraz>} - Relational expressions (<, >, <=, >=)
 *   <li>{@code <aditivni_izraz>} - Additive expressions (+, -)
 *   <li>{@code <multiplikativni_izraz>} - Multiplicative expressions (*, /, %)
 *   <li>{@code <cast_izraz>} - Cast expressions
 *   <li>{@code <unarni_izraz>} - Unary expressions (+, -, !)
 *   <li>{@code <postfiks_izraz>} - Postfix expressions (function calls, array indexing)
 *   <li>{@code <primarni_izraz>} - Primary expressions (identifiers, constants)
 * </ul>
 *
 * <p><b>Subpackages:</b>
 *
 * <ul>
 *   <li>{@code binary} - Binary arithmetic and relational operations
 *   <li>{@code logical} - Logical operations (&&, ||) with short-circuit evaluation
 *   <li>{@code unary} - Unary operations (+, -, !, casts)
 *   <li>{@code assignment} - Assignment operations (=, +=, -=, ++, --)
 *   <li>{@code array} - Array indexing and element access
 *   <li>{@code call} - Function call argument generation
 *   <li>{@code primary} - Primary expressions (identifiers, constants, parentheses)
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.expr.ExpressionCodeGenerator} - Expression orchestrator
 * </ul>
 *
 * <p><b>Expression Evaluation:</b>
 *
 * <ul>
 *   <li>Results typically left in R0
 *   <li>Left operands saved on stack before evaluating right operands
 *   <li>Short-circuit evaluation for && and ||
 *   <li>Type conversions handled according to C semantics
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.expr;
