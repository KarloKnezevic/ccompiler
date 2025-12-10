package hr.fer.ppj.codegen.expr.logical;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for logical expressions with short-circuit evaluation.
 *
 * <p>This class handles the generation of code for logical operators:
 *
 * <ul>
 *   <li><b>Logical OR (||):</b> Short-circuits if left operand is true (non-zero)
 *   <li><b>Logical AND (&&):</b> Short-circuits if left operand is false (zero)
 * </ul>
 *
 * <p><b>Algorithm: Short-Circuit Evaluation</b>
 *
 * <p>This class implements <b>short-circuit evaluation</b>, a compiler optimization technique where
 * the right operand of a logical expression is only evaluated if the result cannot be determined
 * from the left operand alone. This is both a correctness requirement (C standard) and a
 * performance optimization.
 *
 * <p><b>Short-Circuit Evaluation Algorithm:</b>
 *
 * <p>For logical OR (||):
 *
 * <ol>
 *   <li><b>Evaluate Left Operand:</b> Generate code to evaluate the left expression, leaving the
 *       result in R0
 *   <li><b>Check for Short-Circuit:</b> Compare R0 with 0. If R0 != 0 (true), jump to the "true"
 *       label, setting result to 1 and skipping right operand
 *   <li><b>Evaluate Right Operand:</b> If left is false, evaluate the right expression
 *   <li><b>Compute Final Result:</b> If right is true (non-zero), result is 1, else 0
 * </ol>
 *
 * <p>For logical AND (&&):
 *
 * <ol>
 *   <li><b>Evaluate Left Operand:</b> Generate code to evaluate the left expression, leaving the
 *       result in R0
 *   <li><b>Check for Short-Circuit:</b> Compare R0 with 0. If R0 == 0 (false), jump to the "false"
 *       label, setting result to 0 and skipping right operand
 *   <li><b>Evaluate Right Operand:</b> If left is true, evaluate the right expression
 *   <li><b>Compute Final Result:</b> If right is true (non-zero), result is 1, else 0
 * </ol>
 *
 * <p><b>Control Flow Graph Structure:</b>
 *
 * <p>The generated code creates a control flow graph with three basic blocks:
 *
 * <ul>
 *   <li><b>Evaluation Block:</b> Evaluates left operand, checks for short-circuit
 *   <li><b>Right Operand Block:</b> Evaluates right operand (only reached if short-circuit doesn't
 *       occur)
 *   <li><b>Result Blocks:</b> Sets final result (true/false) and jumps to end
 * </ul>
 *
 * <p><b>FRISC Code Pattern for Logical OR (||):</b>
 *
 * <pre>
 * ; Evaluate left operand
 * ... (left expression code) ...
 * CMP R0, %D 0              ; Compare left result with 0
 * JP_NE L_SC_TRUE1          ; If left is true, short-circuit to true
 *
 * ; Left is false, evaluate right operand
 * ... (right expression code) ...
 * CMP R0, %D 0              ; Compare right result with 0
 * JP_NE L_SC_TRUE1          ; If right is true, result is true
 * MOVE %D 0, R0             ; Result is false
 * JP L_SC_END1
 *
 * L_SC_TRUE1:               ; True case
 * MOVE %D 1, R0             ; Result is true
 *
 * L_SC_END1:                ; End of evaluation
 * </pre>
 *
 * <p><b>FRISC Code Pattern for Logical AND (&&):</b>
 *
 * <pre>
 * ; Evaluate left operand
 * ... (left expression code) ...
 * CMP R0, %D 0              ; Compare left result with 0
 * JP_EQ L_SC_FALSE1         ; If left is false, short-circuit to false
 *
 * ; Left is true, evaluate right operand
 * ... (right expression code) ...
 * CMP R0, %D 0              ; Compare right result with 0
 * JP_NE L_SC_TRUE1          ; If right is true, result is true
 * MOVE %D 0, R0             ; Result is false
 * JP L_SC_END1
 *
 * L_SC_TRUE1:               ; True case
 * MOVE %D 1, R0             ; Result is true
 * JP L_SC_END1
 *
 * L_SC_FALSE1:              ; False case (short-circuit)
 * MOVE %D 0, R0             ; Result is false
 *
 * L_SC_END1:                ; End of evaluation
 * </pre>
 *
 * <p><b>Why Short-Circuit Evaluation Matters:</b>
 *
 * <ul>
 *   <li><b>Correctness:</b> The C standard requires short-circuit evaluation. Code like {@code if
 *       (ptr != NULL && ptr->value > 0)} must not dereference a null pointer.
 *   <li><b>Performance:</b> Avoiding unnecessary evaluation of expensive operations (function
 *       calls, complex expressions) improves runtime performance
 *   <li><b>Side Effects:</b> Short-circuit evaluation affects programs that rely on side effects in
 *       the right operand (though such code is generally considered poor style)
 * </ul>
 *
 * <p><b>Register Usage:</b>
 *
 * <ul>
 *   <li><b>R0:</b> Holds the result of expression evaluation and the final boolean result (0 or 1)
 *   <li><b>Other Registers:</b> May be used temporarily during operand evaluation
 * </ul>
 *
 * <p><b>Label Management:</b>
 *
 * <p>This class uses {@link hr.fer.ppj.codegen.util.LabelGenerator#generateShortCircuitLabels()} to
 * generate unique labels for each logical expression. The labels follow the pattern:
 *
 * <ul>
 *   <li>{@code L_SC_TRUE<n>}: Label for the true result case
 *   <li>{@code L_SC_FALSE<n>}: Label for the false result case (AND only)
 *   <li>{@code L_SC_END<n>}: Label for the end of the evaluation
 * </ul>
 *
 * <p><b>Complexity:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions), but the
 *       actual runtime depends on operand evaluation complexity
 *   <li><b>Space Complexity:</b> O(1) for code generation (fixed number of labels and instructions)
 * </ul>
 *
 * <p>Logical expressions return 1 (true) or 0 (false) in register R0, following the C convention
 * where zero is false and any non-zero value is true.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LogicalExpressionGenerator {

  private final CodeGenContext context;
  private final ExpressionCodeGenerator expressionGenerator;

  /**
   * Creates a new logical expression generator.
   *
   * @param context the code generation context
   * @param expressionGenerator the main expression generator for recursive calls
   */
  public LogicalExpressionGenerator(
      CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
  }

  /**
   * Generates code for logical OR expressions (||) with short-circuit evaluation.
   *
   * <p>This method implements the short-circuit evaluation algorithm for logical OR:
   *
   * <ol>
   *   <li><b>Evaluate Left Operand:</b> Recursively generate code for the left expression. The
   *       result is left in R0.
   *   <li><b>Check for Short-Circuit:</b> Compare R0 with 0. If R0 != 0 (left is true), jump to the
   *       true label. This is the short-circuit: we skip evaluating the right operand because the
   *       result is already known to be true.
   *   <li><b>Evaluate Right Operand:</b> If left is false (R0 == 0), we must evaluate the right
   *       operand to determine the final result. Generate code for the right expression, leaving
   *       the result in R0.
   *   <li><b>Compute Final Result:</b> Compare the right operand result with 0. If it's non-zero
   *       (true), jump to the true label. Otherwise, set result to 0 (false) and jump to the end
   *       label.
   *   <li><b>True Case:</b> At the true label, set R0 to 1 (true). This label is reached either
   *       from the short-circuit (left is true) or from the right operand check (right is true).
   * </ol>
   *
   * <p><b>Grammar Rule:</b> Handles {@code <log_ili_izraz>}:
   *
   * <pre>
   * &lt;log_ili_izraz&gt; ::= &lt;log_i_izraz&gt;
   *                   | &lt;log_ili_izraz&gt; OP_ILI &lt;log_i_izraz&gt;
   * </pre>
   *
   * <p><b>FRISC Semantics:</b>
   *
   * <ul>
   *   <li>Uses conditional jumps (JP_NE, JP) to implement control flow
   *   <li>Result is always 0 (false) or 1 (true) in R0
   *   <li>Right operand is only evaluated if left operand is false
   * </ul>
   *
   * <p><b>Side Effects:</b>
   *
   * <ul>
   *   <li>Emits FRISC instructions to the emitter buffer
   *   <li>Generates unique labels for control flow
   *   <li>Leaves the final result (0 or 1) in register R0
   * </ul>
   *
   * @param node the logical OR expression node (must not be null)
   */
  public void generateLogicalOrExpression(NonTerminalNode node) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      // Base case: single child means this is not a binary operation.
      // Delegate to the next level in the expression hierarchy.
      // This handles the grammar rule: <log_ili_izraz> ::= <log_i_izraz>
      expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
    } else if (children.size() == 3) {
      // Binary logical OR: <log_ili_izraz> OP_ILI <log_i_izraz>
      // Children: [left expression, OP_ILI terminal, right expression]
      NonTerminalNode left = (NonTerminalNode) children.get(0);
      NonTerminalNode right = (NonTerminalNode) children.get(2);

      // Generate unique labels for this logical expression's control flow.
      // These labels ensure that nested logical expressions don't interfere
      // with each other.
      var labels = context.labelGenerator().generateShortCircuitLabels();

      // Step 1: Evaluate left operand.
      // This recursively generates code for the left expression, leaving
      // the result in R0. The left expression may itself be a complex
      // expression (nested logical operators, function calls, etc.).
      expressionGenerator.generateExpression(left);

      // Step 2: Check if left operand is true (non-zero).
      // Compare R0 with 0 to determine if the left operand evaluated to true.
      // In C, any non-zero value is considered true, zero is false.
      context.emitter().emitInstruction("CMP", "R0", "%D 0", null);

      // Step 3: Short-circuit if left is true.
      // If R0 != 0 (left is true), jump to the true label. This is the
      // short-circuit optimization: we skip evaluating the right operand
      // because the result is already known to be true (true || anything = true).
      context
          .emitter()
          .emitInstruction("JP_NE", labels.trueLabel(), null, "if left is true, result is true");

      // Step 4: Left is false, evaluate right operand.
      // We only reach this point if the left operand was false (R0 == 0).
      // Now we must evaluate the right operand to determine the final result.
      // The right operand result will be in R0 after this call.
      expressionGenerator.generateExpression(right);

      // Step 5: Check if right operand is true.
      // Compare the right operand result (in R0) with 0.
      context.emitter().emitInstruction("CMP", "R0", "%D 0", null);

      // Step 6: Compute final result based on right operand.
      // If right is true (non-zero), jump to the true label (result = 1).
      // Otherwise, set result to 0 (false) and jump to the end label.
      context
          .emitter()
          .emitInstruction("JP_NE", labels.trueLabel(), null, "if right is true, result is true");
      context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result is false");
      context.emitter().emitInstruction("JP", labels.endLabel(), null, null);

      // Step 7: True case label.
      // This label is reached either:
      // - From the short-circuit (left is true), or
      // - From the right operand check (right is true)
      // In both cases, the result is 1 (true).
      context.emitter().emitLabel(labels.trueLabel());
      context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result is true");

      // Step 8: End label.
      // All code paths converge here. At this point, R0 contains the final
      // result: 0 (false) or 1 (true).
      context.emitter().emitLabel(labels.endLabel());
    }
  }

  /**
   * Generates code for logical AND expressions (&&) with short-circuit evaluation.
   *
   * <p>This method implements the short-circuit evaluation algorithm for logical AND:
   *
   * <ol>
   *   <li><b>Evaluate Left Operand:</b> Recursively generate code for the left expression. The
   *       result is left in R0.
   *   <li><b>Check for Short-Circuit:</b> Compare R0 with 0. If R0 == 0 (left is false), jump to
   *       the false label. This is the short-circuit: we skip evaluating the right operand because
   *       the result is already known to be false (false && anything = false).
   *   <li><b>Evaluate Right Operand:</b> If left is true (R0 != 0), we must evaluate the right
   *       operand to determine the final result. Generate code for the right expression, leaving
   *       the result in R0.
   *   <li><b>Compute Final Result:</b> Compare the right operand result with 0. If it's non-zero
   *       (true), jump to the true label. Otherwise, set result to 0 (false) and jump to the end
   *       label.
   *   <li><b>True Case:</b> At the true label, set R0 to 1 (true). This is only reached if both
   *       operands are true.
   *   <li><b>False Case:</b> At the false label, set R0 to 0 (false). This is reached either from
   *       the short-circuit (left is false) or from the right operand check (right is false).
   * </ol>
   *
   * <p><b>Grammar Rule:</b> Handles {@code <log_i_izraz>}:
   *
   * <pre>
   * &lt;log_i_izraz&gt; ::= &lt;bin_ili_izraz&gt;
   *                 | &lt;log_i_izraz&gt; OP_I &lt;bin_ili_izraz&gt;
   * </pre>
   *
   * <p><b>FRISC Semantics:</b>
   *
   * <ul>
   *   <li>Uses conditional jumps (JP_EQ, JP_NE, JP) to implement control flow
   *   <li>Result is always 0 (false) or 1 (true) in R0
   *   <li>Right operand is only evaluated if left operand is true
   * </ul>
   *
   * <p><b>Key Difference from Logical OR:</b>
   *
   * <ul>
   *   <li>Logical AND short-circuits when the <i>left</i> operand is <i>false</i>
   *   <li>Logical OR short-circuits when the <i>left</i> operand is <i>true</i>
   *   <li>This asymmetry requires different jump conditions (JP_EQ vs JP_NE)
   * </ul>
   *
   * <p><b>Side Effects:</b>
   *
   * <ul>
   *   <li>Emits FRISC instructions to the emitter buffer
   *   <li>Generates unique labels for control flow
   *   <li>Leaves the final result (0 or 1) in register R0
   * </ul>
   *
   * @param node the logical AND expression node (must not be null)
   */
  public void generateLogicalAndExpression(NonTerminalNode node) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      // Base case: single child means this is not a binary operation.
      // Delegate to the next level in the expression hierarchy.
      // This handles the grammar rule: <log_i_izraz> ::= <bin_ili_izraz>
      expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
    } else if (children.size() == 3) {
      // Binary logical AND: <log_i_izraz> OP_I <bin_ili_izraz>
      // Children: [left expression, OP_I terminal, right expression]
      NonTerminalNode left = (NonTerminalNode) children.get(0);
      NonTerminalNode right = (NonTerminalNode) children.get(2);

      // Generate unique labels for this logical expression's control flow.
      // Unlike logical OR, logical AND needs a false label for the short-circuit case.
      var labels = context.labelGenerator().generateShortCircuitLabels();

      // Step 1: Evaluate left operand.
      // This recursively generates code for the left expression, leaving
      // the result in R0.
      expressionGenerator.generateExpression(left);

      // Step 2: Check if left operand is false (zero).
      // Compare R0 with 0 to determine if the left operand evaluated to false.
      context.emitter().emitInstruction("CMP", "R0", "%D 0", null);

      // Step 3: Short-circuit if left is false.
      // If R0 == 0 (left is false), jump to the false label. This is the
      // short-circuit optimization: we skip evaluating the right operand
      // because the result is already known to be false (false && anything = false).
      context
          .emitter()
          .emitInstruction("JP_EQ", labels.falseLabel(), null, "if left is false, result is false");

      // Step 4: Left is true, evaluate right operand.
      // We only reach this point if the left operand was true (R0 != 0).
      // Now we must evaluate the right operand to determine the final result.
      expressionGenerator.generateExpression(right);

      // Step 5: Check if right operand is true.
      // Compare the right operand result (in R0) with 0.
      context.emitter().emitInstruction("CMP", "R0", "%D 0", null);

      // Step 6: Compute final result based on right operand.
      // If right is true (non-zero), jump to the true label (result = 1).
      // Otherwise, set result to 0 (false) and jump to the end label.
      context
          .emitter()
          .emitInstruction("JP_NE", labels.trueLabel(), null, "if right is true, result is true");
      context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result is false");
      context.emitter().emitInstruction("JP", labels.endLabel(), null, null);

      // Step 7: True case label.
      // This label is only reached if both operands are true.
      // Set result to 1 (true) and jump to the end label.
      context.emitter().emitLabel(labels.trueLabel());
      context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result is true");
      context.emitter().emitInstruction("JP", labels.endLabel(), null, null);

      // Step 8: False case label.
      // This label is reached either:
      // - From the short-circuit (left is false), or
      // - From the right operand check (right is false)
      // In both cases, the result is 0 (false).
      context.emitter().emitLabel(labels.falseLabel());
      context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result is false");

      // Step 9: End label.
      // All code paths converge here. At this point, R0 contains the final
      // result: 0 (false) or 1 (true).
      context.emitter().emitLabel(labels.endLabel());
    }
  }
}
