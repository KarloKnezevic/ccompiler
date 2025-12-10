package hr.fer.ppj.codegen.expr.binary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.OperandEvaluator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates FRISC assembly code for integer arithmetic operations.
 *
 * <p>This class handles the generation of code for integer arithmetic operations:
 *
 * <ul>
 *   <li><b>Addition (+):</b> Uses native FRISC ADD instruction
 *   <li><b>Subtraction (-):</b> Uses native FRISC SUB instruction
 *   <li><b>Multiplication (*):</b> Calls F_MUL helper function (FRISC has no native multiply)
 *   <li><b>Division (/):</b> Calls F_DIV helper function (FRISC has no native divide)
 *   <li><b>Modulo (%):</b> Implemented using repeated subtraction algorithm
 * </ul>
 *
 * <p><b>Algorithm: Operand Evaluation Pattern</b>
 *
 * <p>All binary arithmetic operations follow the same operand evaluation pattern, implemented by
 * {@link OperandEvaluator}:
 *
 * <ol>
 *   <li><b>Evaluate Left Operand:</b> Recursively generate code for the left expression, leaving
 *       the result in R0
 *   <li><b>Save Left Operand:</b> Push R0 to the stack to preserve it
 *   <li><b>Evaluate Right Operand:</b> Recursively generate code for the right expression, leaving
 *       the result in R0 (overwrites the left operand value)
 *   <li><b>Restore Operands:</b> Move R0 (right operand) to R1, then pop the left operand back to
 *       R0
 *   <li><b>Perform Operation:</b> Execute the arithmetic operation with operands in R0 and R1
 * </ol>
 *
 * <p><b>FRISC Register Usage:</b>
 *
 * <ul>
 *   <li><b>R0:</b> Left operand (after restoration), then result
 *   <li><b>R1:</b> Right operand
 *   <li><b>R2, R3:</b> Used temporarily in modulo operation
 *   <li><b>Stack:</b> Used to save left operand during right operand evaluation
 * </ul>
 *
 * <p><b>Native vs Helper Function Operations:</b>
 *
 * <p>FRISC has native instructions for addition and subtraction, but not for multiplication or
 * division. This class uses different strategies:
 *
 * <ul>
 *   <li><b>Native Operations (+, -):</b> Direct FRISC instructions (ADD, SUB)
 *   <li><b>Helper Functions (*, /):</b> Call software-implemented helper functions (F_MUL, F_DIV)
 *       that perform the operation using basic instructions
 *   <li><b>Algorithm Implementation (%):</b> Modulo is implemented inline using repeated
 *       subtraction (no helper function needed)
 * </ul>
 *
 * <p><b>Algorithm: Modulo Operation (Repeated Subtraction)</b>
 *
 * <p>The modulo operation (a % b) is implemented using a <b>repeated subtraction algorithm</b>:
 *
 * <ol>
 *   <li><b>Check for Division by Zero:</b> If divisor (b) is 0, result is 0
 *   <li><b>Initialize:</b> Save dividend in R2, divisor in R3
 *   <li><b>Subtraction Loop:</b> Repeatedly subtract divisor from remainder until remainder <
 *       divisor
 *   <li><b>Result:</b> The final remainder (in R2) is the modulo result
 * </ol>
 *
 * <p>This algorithm is used instead of calling a helper function because:
 *
 * <ul>
 *   <li>Modulo is less common than multiplication/division
 *   <li>The algorithm is simple enough to inline (few instructions)
 *   <li>Avoids the overhead of function call/return
 * </ul>
 *
 * <p><b>Helper Function Calling Convention:</b>
 *
 * <p>When calling F_MUL or F_DIV:
 *
 * <ol>
 *   <li><b>Mark Helper as Needed:</b> Call {@code emitter.markMulNeeded()} or {@code
 *       emitter.markDivNeeded()} to ensure the helper is generated
 *   <li><b>Push Arguments:</b> Push arguments on stack in right-to-left order (C convention): right
 *       operand first, then left operand
 *   <li><b>Call Helper:</b> Call the helper function (F_MUL or F_DIV)
 *   <li><b>Clean Up Stack:</b> Add 8 to R7 to remove the two 4-byte arguments
 *   <li><b>Get Result:</b> Move return value from R6 to R0
 * </ol>
 *
 * <p><b>FRISC Code Examples:</b>
 *
 * <p>Addition (a + b):
 *
 * <pre>
 * ... (evaluate a, result in R0) ...
 * PUSH R0                    ; save left operand
 * ... (evaluate b, result in R0) ...
 * MOVE R0, R1                ; right operand to R1
 * POP R0                     ; restore left operand to R0
 * ADD R0, R1, R0             ; result = left + right
 * </pre>
 *
 * <p>Multiplication (a * b):
 *
 * <pre>
 * ... (evaluate operands) ...
 * PUSH R1                    ; push second arg (right operand)
 * PUSH R0                    ; push first arg (left operand)
 * CALL F_MUL                 ; call multiplication helper
 * ADD R7, %D 8, R7           ; clean up arguments (2 * 4 bytes)
 * MOVE R6, R0                ; move result to R0
 * </pre>
 *
 * <p>Modulo (a % b):
 *
 * <pre>
 * ... (evaluate operands) ...
 * CMP R1, %D 0               ; check for modulo by zero
 * JP_EQ L_MOD_ZERO           ; if zero, result is 0
 * MOVE R0, R2                ; save dividend
 * MOVE R1, R3                ; save divisor
 * L_LOOP1:                   ; modulo loop
 *     CMP R2, R3             ; compare remainder with divisor
 *     JP_SLT L_BREAK1        ; exit if remainder < divisor
 *     SUB R2, R3, R2         ; subtract divisor from remainder
 *     JP L_LOOP1             ; continue
 * L_MOD_ZERO:
 *     MOVE %D 0, R2          ; result 0 for modulo by zero
 * L_BREAK1:
 *     MOVE R2, R0            ; move remainder to result
 * </pre>
 *
 * <p><b>Complexity:</b>
 *
 * <ul>
 *   <li><b>Addition/Subtraction:</b> O(1) code generation, O(1) runtime
 *   <li><b>Multiplication/Division:</b> O(1) code generation, O(n) runtime where n is the number of
 *       bits (helper functions use iterative algorithms)
 *   <li><b>Modulo:</b> O(1) code generation, O(a/b) runtime in worst case (repeated subtraction)
 * </ul>
 *
 * <p>For float arithmetic operations, see {@link FloatOperationGenerator}.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArithmeticOperationGenerator {

  private final CodeGenContext context;
  private final OperandEvaluator operandEvaluator;

  /**
   * Creates a new arithmetic operation generator.
   *
   * @param context the code generation context
   * @param expressionGenerator the expression generator for recursive calls
   */
  public ArithmeticOperationGenerator(
      CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.operandEvaluator = new OperandEvaluator(context, expressionGenerator);
  }

  /**
   * Generates code for addition operation.
   *
   * @param left the left operand
   * @param right the right operand
   */
  public void generateAddition(NonTerminalNode left, NonTerminalNode right) {
    operandEvaluator.evaluateOperands(left, right);
    context.emitter().emitInstruction("ADD", "R0", "R1", "R0", "addition");
  }

  /**
   * Generates code for subtraction operation.
   *
   * @param left the left operand
   * @param right the right operand
   */
  public void generateSubtraction(NonTerminalNode left, NonTerminalNode right) {
    operandEvaluator.evaluateOperands(left, right);
    context.emitter().emitInstruction("SUB", "R0", "R1", "R0", "subtraction");
  }

  /**
   * Generates code for multiplication using the F_MUL helper function.
   *
   * @param left the left operand (multiplicand)
   * @param right the right operand (multiplier)
   */
  public void generateMultiplication(NonTerminalNode left, NonTerminalNode right) {
    operandEvaluator.evaluateOperands(left, right);

    // Mark that F_MUL helper is needed
    context.emitter().markMulNeeded();

    // Push arguments (right, then left), call F_MUL, clean up
    context.emitter().emitInstruction("PUSH", "R1", null, "push second arg (right operand)");
    context.emitter().emitInstruction("PUSH", "R0", null, "push first arg (left operand)");
    context.emitter().emitInstruction("CALL", "F_MUL", null, "call multiplication helper");
    context.emitter().emitInstruction("ADD", "R7", "%D 8", "R7", "clean up arguments");

    // Result is in R6, move to R0
    context.emitter().emitInstruction("MOVE", "R6", "R0", "move result to R0");
  }

  /**
   * Generates code for division using the F_DIV helper function.
   *
   * @param left the left operand (dividend)
   * @param right the right operand (divisor)
   */
  public void generateDivision(NonTerminalNode left, NonTerminalNode right) {
    operandEvaluator.evaluateOperands(left, right);

    // Mark that F_DIV helper is needed
    context.emitter().markDivNeeded();

    // Push arguments (right, then left), call F_DIV, clean up
    context.emitter().emitInstruction("PUSH", "R1", null, "push second arg (divisor)");
    context.emitter().emitInstruction("PUSH", "R0", null, "push first arg (dividend)");
    context.emitter().emitInstruction("CALL", "F_DIV", null, "call division helper");
    context.emitter().emitInstruction("ADD", "R7", "%D 8", "R7", "clean up arguments");

    // Result is in R6, move to R0
    context.emitter().emitInstruction("MOVE", "R6", "R0", "move result to R0");
  }

  /**
   * Generates code for modulo operation using repeated subtraction.
   *
   * <p>This method implements the <b>repeated subtraction algorithm</b> for computing modulo (a %
   * b). The algorithm works by repeatedly subtracting the divisor from the dividend until the
   * remainder is less than the divisor.
   *
   * <p><b>Algorithm Steps:</b>
   *
   * <ol>
   *   <li><b>Division by Zero Check:</b> If the divisor is 0, the result is 0 (C standard behavior,
   *       though undefined in some contexts)
   *   <li><b>Initialize Registers:</b> Save dividend in R2, divisor in R3
   *   <li><b>Subtraction Loop:</b> While remainder (R2) >= divisor (R3):
   *       <ul>
   *         <li>Subtract divisor from remainder: R2 = R2 - R3
   *         <li>Continue loop
   *       </ul>
   *   <li><b>Result:</b> The final value in R2 is the modulo result
   * </ol>
   *
   * <p><b>Why Repeated Subtraction Instead of Division?</b>
   *
   * <ul>
   *   <li>FRISC has no native division instruction
   *   <li>Modulo can be computed without the quotient, so we only need the remainder
   *   <li>For small divisors, repeated subtraction is efficient
   *   <li>Avoids the overhead of calling a helper function
   * </ul>
   *
   * <p><b>Edge Cases:</b>
   *
   * <ul>
   *   <li><b>Division by Zero:</b> If divisor is 0, result is 0 (handled by early exit)
   *   <li><b>Negative Operands:</b> This implementation assumes non-negative operands. Negative
   *       modulo semantics are implementation-defined in C.
   *   <li><b>Large Dividends:</b> The loop may iterate many times for large dividends, but this is
   *       acceptable for the target architecture
   * </ul>
   *
   * <p><b>FRISC Register Usage:</b>
   *
   * <ul>
   *   <li><b>R0:</b> Initially left operand (dividend), finally result (remainder)
   *   <li><b>R1:</b> Right operand (divisor)
   *   <li><b>R2:</b> Working remainder (starts as dividend, decreases during loop)
   *   <li><b>R3:</b> Saved divisor (constant during loop)
   * </ul>
   *
   * @param left the left operand (dividend) expression node
   * @param right the right operand (divisor) expression node
   */
  public void generateModulo(NonTerminalNode left, NonTerminalNode right) {
    // Step 1: Evaluate operands using the standard pattern.
    // After this, R0 contains left operand (dividend), R1 contains right operand (divisor).
    operandEvaluator.evaluateOperands(left, right);

    // Generate unique labels for the modulo loop and error handling.
    // We use loop labels for the subtraction loop, and a separate label for division by zero.
    var labels = context.labelGenerator().generateLoopLabels();
    String modByZero = context.labelGenerator().generateLabel();

    context.emitter().emitComment("Modulo: R0 % R1");

    // Step 2: Check for division by zero.
    // If the divisor is 0, we cannot perform modulo. The C standard says modulo by zero
    // is undefined behavior, but we handle it gracefully by returning 0.
    context.emitter().emitInstruction("CMP", "R1", "%D 0", null);
    context.emitter().emitInstruction("JP_EQ", modByZero, "modulo by zero");

    // Step 3: Save operands to working registers.
    // We need to preserve the original operands because R0 and R1 will be used
    // for the loop. R2 will hold the working remainder, R3 will hold the divisor.
    context.emitter().emitInstruction("MOVE", "R0", "R2", "save dividend");
    context.emitter().emitInstruction("MOVE", "R1", "R3", "save divisor");

    // Step 4: Modulo loop - repeated subtraction until remainder < divisor.
    // The loop condition is: while (remainder >= divisor) { remainder -= divisor; }
    // This is equivalent to: while (remainder >= divisor) subtract divisor from remainder.
    context.emitter().emitLabel(labels.loopLabel(), "modulo loop");

    // Compare remainder (R2) with divisor (R3).
    // If remainder < divisor, we're done (exit loop).
    context.emitter().emitInstruction("CMP", "R2", "R3", "compare remainder with divisor");
    context.emitter().emitInstruction("JP_SLT", labels.breakLabel(), "exit if remainder < divisor");

    // Subtract divisor from remainder: R2 = R2 - R3.
    // This is the core of the repeated subtraction algorithm.
    context.emitter().emitInstruction("SUB", "R2", "R3", "R2", "subtract divisor from remainder");

    // Continue the loop to check if more subtractions are needed.
    context.emitter().emitInstruction("JP", labels.loopLabel(), "continue modulo");

    // Step 5: Handle division by zero case.
    // If divisor was 0, set result to 0 and jump to end.
    context.emitter().emitLabel(modByZero, "modulo by zero");
    context.emitter().emitInstruction("MOVE", "%D 0", "R2", "result 0 for modulo by zero");

    // Step 6: End of modulo - move result to R0.
    // At this point, R2 contains the final remainder (modulo result).
    // Move it to R0 to follow the convention that expression results are in R0.
    context.emitter().emitLabel(labels.breakLabel(), "end modulo");
    context.emitter().emitInstruction("MOVE", "R2", "R0", "move remainder to result");
  }
}
