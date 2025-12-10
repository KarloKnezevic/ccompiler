package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Utility class for evaluating operands in binary operations.
 *
 * <p>This class encapsulates the common pattern of evaluating left and right operands for binary
 * operations, including stack management and type conversion. It implements the <b>operand
 * evaluation pattern</b> used by all binary expression generators, eliminating code duplication and
 * ensuring consistent operand handling.
 *
 * <p><b>Design Pattern: Template Method for Operand Evaluation</b>
 *
 * <p>This class implements a <b>template method pattern</b> for operand evaluation:
 *
 * <ul>
 *   <li><b>Standard Evaluation:</b> {@code evaluateOperands()} - for integer operations
 *   <li><b>Float Evaluation:</b> {@code evaluateOperandsWithFloatConversion()} - for float
 *       operations with automatic int-to-float conversion
 * </ul>
 *
 * <p><b>Algorithm: Operand Evaluation Pattern</b>
 *
 * <p>The standard operand evaluation algorithm works as follows:
 *
 * <ol>
 *   <li><b>Evaluate Left Operand:</b> Recursively generate code for the left expression. The result
 *       is left in R0. This may involve complex nested expressions, function calls, or simple
 *       constants.
 *   <li><b>Save Left Operand:</b> Push R0 to the stack to preserve it. This is necessary because
 *       evaluating the right operand will overwrite R0.
 *   <li><b>Evaluate Right Operand:</b> Recursively generate code for the right expression. The
 *       result is left in R0, overwriting the left operand value that was there.
 *   <li><b>Restore Operands:</b> Move R0 (right operand) to R1, then pop the left operand back to
 *       R0. After this step:
 *       <ul>
 *         <li>R0 contains the left operand
 *         <li>R1 contains the right operand
 *       </ul>
 * </ol>
 *
 * <p><b>Why This Pattern?</b>
 *
 * <p>This pattern is necessary because:
 *
 * <ul>
 *   <li><b>Register Scarcity:</b> FRISC has limited registers (R0-R7), and R0 is the standard
 *       result register. We cannot keep both operands in registers simultaneously during right
 *       operand evaluation.
 *   <li><b>Recursive Evaluation:</b> Right operand evaluation may itself be a complex expression
 *       that uses R0, so we must save the left operand before evaluating right.
 *   <li><b>Stack as Temporary Storage:</b> The stack provides a convenient temporary storage
 *       location that is automatically managed and doesn't interfere with register allocation.
 * </ul>
 *
 * <p><b>FRISC Code Pattern:</b>
 *
 * <pre>
 * ; Evaluate left operand
 * ... (left expression code, result in R0) ...
 * PUSH R0                    ; save left operand on stack
 *
 * ; Evaluate right operand
 * ... (right expression code, result in R0) ...
 * MOVE R0, R1                ; move right operand to R1
 * POP R0                     ; restore left operand to R0
 *
 * ; Now R0 = left operand, R1 = right operand
 * ; Ready for binary operation (ADD, SUB, CMP, etc.)
 * </pre>
 *
 * <p><b>Float Conversion Variant:</b>
 *
 * <p>The {@code evaluateOperandsWithFloatConversion()} method extends the standard pattern with
 * automatic type conversion:
 *
 * <ol>
 *   <li>Evaluate left operand
 *   <li>If left is not float, convert int/char to float using F_I2F helper
 *   <li>Save left operand (now in float format)
 *   <li>Evaluate right operand
 *   <li>If right is not float, convert int/char to float using F_I2F helper
 *   <li>Restore operands (both now in float format)
 * </ol>
 *
 * <p>This handles C's <b>usual arithmetic conversions</b>, where int/char operands are
 * automatically promoted to float when mixed with float operands.
 *
 * <p><b>Stack Management:</b>
 *
 * <p>This class manages the stack correctly:
 *
 * <ul>
 *   <li><b>Push Before Right Evaluation:</b> Left operand is pushed before right evaluation to
 *       preserve it
 *   <li><b>Pop After Right Evaluation:</b> Left operand is popped after right evaluation to restore
 *       it
 *   <li><b>Stack Balance:</b> Each push is matched with exactly one pop, maintaining stack balance
 * </ul>
 *
 * <p><b>Register Usage:</b>
 *
 * <ul>
 *   <li><b>R0:</b> Expression result register (left operand after restoration)
 *   <li><b>R1:</b> Right operand register
 *   <li><b>Stack:</b> Temporary storage for left operand during right evaluation
 * </ul>
 *
 * <p><b>Complexity:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for the evaluation pattern itself, but the actual time depends
 *       on the complexity of the operand expressions
 *   <li><b>Space Complexity:</b> O(1) stack space (one 4-byte value pushed/popped)
 * </ul>
 *
 * <p>This pattern is used by all binary operations (arithmetic, bitwise, comparisons).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class OperandEvaluator {

  private final CodeGenContext context;
  private final ExpressionCodeGenerator expressionGenerator;

  /**
   * Creates a new operand evaluator.
   *
   * @param context the code generation context
   * @param expressionGenerator the expression generator for evaluating operands
   */
  public OperandEvaluator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
  }

  /**
   * Evaluates left and right operands for a binary operation.
   *
   * <p>This method implements the standard operand evaluation pattern used by all binary
   * operations. It ensures that after evaluation:
   *
   * <ul>
   *   <li>R0 contains the left operand
   *   <li>R1 contains the right operand
   * </ul>
   *
   * <p><b>Preconditions:</b>
   *
   * <ul>
   *   <li>Both operands must be valid expression nodes
   *   <li>Stack must have sufficient space for one 4-byte push
   * </ul>
   *
   * <p><b>Postconditions:</b>
   *
   * <ul>
   *   <li>R0 contains the left operand value
   *   <li>R1 contains the right operand value
   *   <li>Stack is balanced (no net change in stack pointer)
   * </ul>
   *
   * <p><b>Side Effects:</b>
   *
   * <ul>
   *   <li>Emits FRISC instructions to evaluate both operands
   *   <li>Temporarily uses stack space (one 4-byte push/pop)
   *   <li>May use other registers during operand evaluation (R0, R1, etc.)
   * </ul>
   *
   * @param left the left operand expression node (must not be null)
   * @param right the right operand expression node (must not be null)
   */
  public void evaluateOperands(NonTerminalNode left, NonTerminalNode right) {
    // Step 1: Evaluate left operand.
    // This recursively generates code for the left expression. The result is
    // left in R0, following the standard expression evaluation convention.
    // The left expression may be arbitrarily complex (nested expressions,
    // function calls, etc.), but the result is always in R0.
    expressionGenerator.generateExpression(left);

    // Step 2: Save left operand on stack.
    // We push R0 to the stack to preserve it, because evaluating the right
    // operand will overwrite R0. The stack provides temporary storage that
    // doesn't interfere with register allocation during right operand evaluation.
    context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");

    // Step 3: Evaluate right operand.
    // This recursively generates code for the right expression. The result is
    // left in R0, overwriting the left operand value that was there. This is
    // why we needed to save the left operand on the stack.
    expressionGenerator.generateExpression(right);

    // Step 4: Move right operand to R1.
    // Now R0 contains the right operand. We move it to R1 to free up R0 for
    // the left operand restoration.
    context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");

    // Step 5: Restore left operand to R0.
    // Pop the saved left operand from the stack back into R0. After this,
    // R0 contains the left operand and R1 contains the right operand, which
    // is exactly what we need for binary operations.
    context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
  }

  /**
   * Evaluates operands with automatic float type conversion if needed.
   *
   * <p>This method extends the standard operand evaluation pattern with automatic type conversion
   * for float operations. It implements C's <b>usual arithmetic conversions</b>, where integer
   * operands are automatically promoted to float when mixed with float operands.
   *
   * <p><b>Type Conversion Rules:</b>
   *
   * <ul>
   *   <li>If an operand is already float, no conversion is needed
   *   <li>If an operand is int or char, it is converted to float using F_I2F helper
   *   <li>After conversion, both operands are in Q16.16 fixed-point format
   * </ul>
   *
   * <p><b>Algorithm:</b>
   *
   * <ol>
   *   <li>Evaluate left operand (result in R0)
   *   <li>Check left operand type. If not float, convert int/char to float using F_I2F
   *   <li>Save left operand (now in float format) on stack
   *   <li>Evaluate right operand (result in R0)
   *   <li>Check right operand type. If not float, convert int/char to float using F_I2F
   *   <li>Move right operand to R1, restore left operand to R0
   * </ol>
   *
   * <p><b>Postconditions:</b>
   *
   * <ul>
   *   <li>R0 contains the left operand (as float, Q16.16 format)
   *   <li>R1 contains the right operand (as float, Q16.16 format)
   *   <li>Both operands are in the same format, ready for float operations
   * </ul>
   *
   * <p><b>Q16.16 Fixed-Point Format:</b>
   *
   * <p>Floats are represented as 32-bit signed integers in Q16.16 fixed-point format:
   *
   * <ul>
   *   <li>16 high bits: integer part
   *   <li>16 low bits: fractional part (scaled by 65536)
   *   <li>Example: 1.5 is represented as 98304 (1 * 65536 + 0.5 * 65536)
   * </ul>
   *
   * <p><b>Side Effects:</b>
   *
   * <ul>
   *   <li>Emits FRISC instructions to evaluate both operands
   *   <li>May call F_I2F helper function for type conversion
   *   <li>Marks F_I2F as needed if conversion is performed
   *   <li>Uses stack space temporarily (one 4-byte push/pop)
   * </ul>
   *
   * @param left the left operand expression node (must not be null)
   * @param right the right operand expression node (must not be null)
   */
  public void evaluateOperandsWithFloatConversion(NonTerminalNode left, NonTerminalNode right) {
    // Step 1: Evaluate left operand.
    // Generate code for the left expression, leaving the result in R0.
    expressionGenerator.generateExpression(left);

    // Step 2: Check left operand type and convert if needed.
    // Extract the semantic type from the parse tree node's attributes.
    // If the type is not float, we need to convert it to float format.
    Type leftType = getExpressionType(left);

    // Convert int/char to float if needed.
    // This implements C's usual arithmetic conversions: when an int/char
    // is used with a float, it is automatically promoted to float.
    // The conversion uses the F_I2F helper function, which converts a 32-bit
    // integer to Q16.16 fixed-point format.
    if (leftType != PrimitiveType.FLOAT) {
      convertIntToFloat();
    }

    // Step 3: Save left operand (now in float format) on stack.
    // The left operand is now in R0, in Q16.16 format if conversion was needed.
    context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");

    // Step 4: Evaluate right operand.
    // Generate code for the right expression, leaving the result in R0.
    // This overwrites the left operand value, which is why we saved it.
    expressionGenerator.generateExpression(right);

    // Step 5: Check right operand type and convert if needed.
    // Same logic as for the left operand: if it's not float, convert it.
    Type rightType = getExpressionType(right);

    // Convert int/char to float if needed.
    // After this, both operands are guaranteed to be in float format.
    if (rightType != PrimitiveType.FLOAT) {
      convertIntToFloat();
    }

    // Step 6: Restore operands to R0 and R1.
    // Move right operand (in R0) to R1, then pop left operand back to R0.
    // After this, both operands are in float format and ready for float operations.
    context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
    context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
  }

  /**
   * Gets the type of an expression from its semantic attributes.
   *
   * @param node the expression node
   * @return the type, or null if not available
   */
  private Type getExpressionType(NonTerminalNode node) {
    if (node != null && node.attributes() != null) {
      return node.attributes().type();
    }
    return null;
  }

  /**
   * Converts an integer value in R0 to float (Q16.16) format.
   *
   * <p>This method calls the F_I2F helper function to convert a 32-bit signed integer to Q16.16
   * fixed-point format. The conversion multiplies the integer by 65536 (2^16) to scale it into the
   * fixed-point representation.
   *
   * <p><b>Conversion Algorithm:</b>
   *
   * <ul>
   *   <li>Input: 32-bit signed integer in R0
   *   <li>Process: Multiply by 65536 (left shift by 16 bits)
   *   <li>Output: Q16.16 fixed-point value in R0
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <ul>
   *   <li>Integer 1 → Float 65536 (1.0 in Q16.16)
   *   <li>Integer 2 → Float 131072 (2.0 in Q16.16)
   *   <li>Integer -1 → Float -65536 (-1.0 in Q16.16)
   * </ul>
   *
   * <p><b>FRISC Calling Convention:</b>
   *
   * <ol>
   *   <li>Mark F_I2F as needed (ensures helper is generated)
   *   <li>Push integer argument on stack
   *   <li>Call F_I2F helper function
   *   <li>Clean up stack (remove 4-byte argument)
   *   <li>Move return value from R6 to R0
   * </ol>
   *
   * <p><b>Preconditions:</b>
   *
   * <ul>
   *   <li>R0 contains a 32-bit signed integer value
   * </ul>
   *
   * <p><b>Postconditions:</b>
   *
   * <ul>
   *   <li>R0 contains the float value in Q16.16 format
   *   <li>Stack is balanced (argument pushed and cleaned up)
   * </ul>
   *
   * <p><b>Side Effects:</b>
   *
   * <ul>
   *   <li>Marks F_I2F helper as needed
   *   <li>Emits FRISC instructions for the conversion
   *   <li>Temporarily uses stack space (one 4-byte push)
   * </ul>
   */
  private void convertIntToFloat() {
    // Mark that the F_I2F helper function is needed.
    // This ensures that the helper will be generated at the end of code generation.
    context.emitter().markIntToFloatNeeded();

    // Push the integer value as an argument to F_I2F.
    // The helper function expects the integer value on the stack.
    context.emitter().emitInstruction("PUSH", "R0", null, "push integer value");

    // Call the F_I2F helper function.
    // This function performs the conversion: multiplies the integer by 65536
    // to convert it to Q16.16 fixed-point format. The result is returned in R6.
    context.emitter().emitInstruction("CALL", "F_I2F", null, "convert int to float");

    // Clean up the argument from the stack.
    // Add 4 to R7 to remove the 4-byte integer argument that was pushed.
    context.emitter().emitInstruction("ADD", "R7", "%D 4", "R7", "cleanup argument");

    // Move the return value from R6 to R0.
    // F_I2F returns the float value in R6 (following FRISC calling convention),
    // but we need it in R0 to continue with the expression evaluation.
    context.emitter().emitInstruction("MOVE", "R6", "R0", "move float result to R0");
  }
}
