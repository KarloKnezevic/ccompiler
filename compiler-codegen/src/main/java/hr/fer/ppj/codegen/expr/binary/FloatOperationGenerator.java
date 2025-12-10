package hr.fer.ppj.codegen.expr.binary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.FloatHelperCaller;
import hr.fer.ppj.codegen.utils.OperandEvaluator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates FRISC assembly code for Q16.16 fixed-point float operations.
 *
 * <p>This class handles the generation of code for all floating-point operations by delegating to
 * specialized helper functions. Float operations use <b>Q16.16 fixed-point representation</b> and
 * call helper functions (F_FADD, F_FSUB, F_FMUL, F_FDIV, F_FCMP) for arithmetic and comparison.
 *
 * <p><b>Algorithm: Float Operation Code Generation</b>
 *
 * <p>All float operations follow a common pattern:
 *
 * <ol>
 *   <li><b>Operand Evaluation with Type Conversion:</b>
 *       <ul>
 *         <li>Evaluate left operand (may be int, char, or float)
 *         <li>If operand is not float, convert to float using F_I2F
 *         <li>Save left operand (now in Q16.16 format) on stack
 *         <li>Evaluate right operand (may be int, char, or float)
 *         <li>If operand is not float, convert to float using F_I2F
 *         <li>Restore operands to R0 and R1 (both in Q16.16 format)
 *       </ul>
 *   <li><b>Helper Function Call:</b>
 *       <ul>
 *         <li>Push arguments on stack (right-to-left order)
 *         <li>Call the appropriate float helper function
 *         <li>Clean up arguments from stack
 *         <li>Move return value from R6 to R0
 *       </ul>
 *   <li><b>Result Processing:</b>
 *       <ul>
 *         <li>For comparisons: Convert three-way comparison result (-1, 0, 1) to boolean (0, 1)
 *         <li>For arithmetic: Result is already in Q16.16 format in R0
 *       </ul>
 * </ol>
 *
 * <p><b>Q16.16 Fixed-Point Representation:</b>
 *
 * <p>Floats are represented as 32-bit signed integers in Q16.16 format:
 *
 * <ul>
 *   <li>Bits 31-16: Integer part (signed 16-bit)
 *   <li>Bits 15-0: Fractional part (unsigned 16-bit, scaled by 65536)
 *   <li>Actual value = stored_integer / 65536.0
 * </ul>
 *
 * <p><b>Type Conversion (Usual Arithmetic Conversions):</b>
 *
 * <p>C standard specifies that when an integer operand is used with a float operand, the integer is
 * automatically promoted to float. This is implemented by:
 *
 * <ul>
 *   <li>Checking the type of each operand
 *   <li>If operand is int or char, calling F_I2F to convert to Q16.16
 *   <li>If operand is already float, no conversion is needed
 * </ul>
 *
 * <p><b>Float Comparison Algorithm:</b>
 *
 * <p>Float comparisons use a three-way comparison helper (F_FCMP) that returns:
 *
 * <ul>
 *   <li><b>-1:</b> if a < b
 *   <li><b>0:</b> if a == b
 *   <li><b>1:</b> if a > b
 * </ul>
 *
 * <p>This result is then converted to a boolean (0 or 1) based on the comparison operator:
 *
 * <ul>
 *   <li><b>== (OP_EQ):</b> result == 0 → 1, else → 0
 *   <li><b>!= (OP_NEQ):</b> result != 0 → 1, else → 0
 *   <li><b>&lt; (OP_LT):</b> result < 0 → 1, else → 0
 *   <li><b>&gt; (OP_GT):</b> result > 0 → 1, else → 0
 *   <li><b>&lt;= (OP_LTE):</b> result <= 0 → 1, else → 0
 *   <li><b>&gt;= (OP_GTE):</b> result >= 0 → 1, else → 0
 * </ul>
 *
 * <p><b>FRISC Register Usage:</b>
 *
 * <ul>
 *   <li><b>R0:</b> Left operand (after conversion), then result
 *   <li><b>R1:</b> Right operand (after conversion)
 *   <li><b>R6:</b> Return value from helper functions
 *   <li><b>Stack:</b> Used to save left operand during right operand evaluation
 * </ul>
 *
 * <p><b>FRISC Code Pattern (Float Addition):</b>
 *
 * <pre>
 * ; Evaluate left operand
 * ... (left expression code, result in R0) ...
 * ; Convert to float if needed
 * PUSH R0
 * CALL F_I2F
 * ADD R7, %D 4, R7
 * MOVE R6, R0
 * PUSH R0                    ; save left operand
 *
 * ; Evaluate right operand
 * ... (right expression code, result in R0) ...
 * ; Convert to float if needed
 * PUSH R0
 * CALL F_I2F
 * ADD R7, %D 4, R7
 * MOVE R6, R0
 * MOVE R0, R1                ; right operand to R1
 * POP R0                     ; restore left operand
 *
 * ; Call float helper
 * PUSH R1                    ; push second arg
 * PUSH R0                    ; push first arg
 * CALL F_FADD                ; call float addition
 * ADD R7, %D 8, R7           ; clean up arguments
 * MOVE R6, R0                ; move result to R0
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation, but actual runtime depends on the helper
 *       function complexity (O(1) for add/sub, O(32) for mul, O(64) for div)
 *   <li><b>Space Complexity:</b> O(1) - uses only registers and temporary stack space
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatOperationGenerator {

  private final CodeGenContext context;
  private final OperandEvaluator operandEvaluator;
  private final FloatHelperCaller floatHelperCaller;

  /**
   * Creates a new float operation generator.
   *
   * @param context the code generation context
   * @param expressionGenerator the expression generator for recursive calls
   */
  public FloatOperationGenerator(
      CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.operandEvaluator = new OperandEvaluator(context, expressionGenerator);
    this.floatHelperCaller = new FloatHelperCaller(context);
  }

  /**
   * Generates code for float addition or subtraction.
   *
   * @param left the left operand
   * @param right the right operand
   * @param operation "PLUS" for addition, "MINUS" for subtraction
   */
  public void generateAdditiveOperation(
      NonTerminalNode left, NonTerminalNode right, String operation) {
    operandEvaluator.evaluateOperandsWithFloatConversion(left, right);

    // Call float helper function
    if ("PLUS".equals(operation)) {
      floatHelperCaller.callFloatHelper("F_FADD", () -> context.emitter().markFloatAddNeeded());
    } else if ("MINUS".equals(operation)) {
      floatHelperCaller.callFloatHelper("F_FSUB", () -> context.emitter().markFloatSubNeeded());
    }
  }

  /**
   * Generates code for float multiplication.
   *
   * @param left the left operand
   * @param right the right operand
   */
  public void generateMultiplication(NonTerminalNode left, NonTerminalNode right) {
    operandEvaluator.evaluateOperandsWithFloatConversion(left, right);
    floatHelperCaller.callFloatHelper("F_FMUL", () -> context.emitter().markFloatMulNeeded());
  }

  /**
   * Generates code for float division.
   *
   * @param left the left operand
   * @param right the right operand
   */
  public void generateDivision(NonTerminalNode left, NonTerminalNode right) {
    operandEvaluator.evaluateOperandsWithFloatConversion(left, right);
    floatHelperCaller.callFloatHelper("F_FDIV", () -> context.emitter().markFloatDivNeeded());
  }

  /**
   * Generates code for float comparison.
   *
   * @param left the left operand
   * @param right the right operand
   * @param operator the comparison operator
   */
  public void generateComparison(NonTerminalNode left, NonTerminalNode right, String operator) {
    operandEvaluator.evaluateOperandsWithFloatConversion(left, right);

    // Call float comparison helper
    context.emitter().markFloatCmpNeeded();
    context.emitter().emitInstruction("PUSH", "R1", null, "push second arg");
    context.emitter().emitInstruction("PUSH", "R0", null, "push first arg");
    context.emitter().emitInstruction("CALL", "F_FCMP", null, "call float comparison");
    context.emitter().emitInstruction("ADD", "R7", "%D 8", "R7", "cleanup arguments");

    // F_FCMP returns -1, 0, or 1 in R6
    // Convert to boolean (0 or 1) based on operator
    context.emitter().emitInstruction("MOVE", "R6", "R0", "comparison result");

    var labels = context.labelGenerator().generateShortCircuitLabels();

    // Map comparison result to boolean based on operator
    switch (operator) {
      case "OP_EQ" -> {
        // == : result == 0 -> true (1), else false (0)
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", labels.trueLabel(), "if equal");
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null);
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result true");
      }
      case "OP_NEQ" -> {
        // != : result != 0 -> true (1), else false (0)
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_NE", labels.trueLabel(), "if not equal");
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null);
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result true");
      }
      case "OP_LT" -> {
        // < : result < 0 -> true (1), else false (0)
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_SLT", labels.trueLabel(), "if less");
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null);
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result true");
      }
      case "OP_GT" -> {
        // > : result > 0 -> true (1), else false (0)
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_SGT", labels.trueLabel(), "if greater");
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null);
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result true");
      }
      case "OP_LTE" -> {
        // <= : result <= 0 -> true (1), else false (0)
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_SLE", labels.trueLabel(), "if less or equal");
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null);
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result true");
      }
      case "OP_GTE" -> {
        // >= : result >= 0 -> true (1), else false (0)
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_SGE", labels.trueLabel(), "if greater or equal");
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null);
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result true");
      }
      default -> {
        // Fallback: use comparison result as-is
        context.emitter().emitComment("Unknown comparison operator: " + operator);
      }
    }

    context.emitter().emitLabel(labels.endLabel());
  }
}
