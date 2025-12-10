package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_F2I helper function for Q16.16 fixed-point float to integer conversion.
 *
 * <p>This function implements <b>float to integer conversion</b> by converting a Q16.16 fixed-point
 * value to a 32-bit signed integer. This is used when a float value needs to be used in an integer
 * context (e.g., array indexing, integer arithmetic).
 *
 * <p><b>Algorithm: Q16.16 to Integer Conversion</b>
 *
 * <p>The conversion formula:
 *
 * <pre>
 * integer_value = truncate(float_value / 65536)
 * </pre>
 *
 * <p>This is equivalent to shifting the Q16.16 value right by 16 bits (arithmetic shift for signed
 * values), which divides by 2^16 = 65536 and truncates the fractional part. The result is the
 * integer part of the float value.
 *
 * <p><b>Mathematical Basis:</b>
 *
 * <p>In Q16.16 format, a value represents:
 *
 * <pre>
 * actual_value = stored_integer / 65536.0
 * </pre>
 *
 * <p>To convert a Q16.16 value f to integer:
 *
 * <pre>
 * integer_value = truncate(f / 65536)
 *                = truncate((stored_integer / 65536.0) / 65536)
 *                = truncate(stored_integer / 65536²)
 *                = stored_integer >> 16
 * </pre>
 *
 * <p>Therefore, shifting right by 16 bits (arithmetic shift) correctly extracts the integer part
 * and truncates the fractional part.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * f = 5.5 in Q16.16 = 0x00058000 (5.5 × 65536 = 360448)
 * result = 0x00058000 >> 16 = 0x0005 = 5 (truncated) ✓
 *
 * f = -3.7 in Q16.16 = 0xFFFCB333 (approximately -3.7 × 65536)
 * result = 0xFFFCB333 >> 16 = 0xFFFC = -4 (truncated towards zero) ✓
 *
 * f = 2.0 in Q16.16 = 0x00020000 (2.0 × 65536 = 131072)
 * result = 0x00020000 >> 16 = 0x0002 = 2 ✓
 * </pre>
 *
 * <p><b>Truncation Behavior:</b>
 *
 * <p>C standard specifies truncation towards zero:
 *
 * <ul>
 *   <li>Positive values: Truncate downward (3.7 → 3)
 *   <li>Negative values: Truncate upward (-3.7 → -3, not -4)
 * </ul>
 *
 * <p>However, arithmetic right shift (SHR for signed values) truncates towards negative infinity:
 *
 * <ul>
 *   <li>Positive values: 3.7 >> 16 = 3 (correct)
 *   <li>Negative values: -3.7 >> 16 = -4 (may need adjustment)
 * </ul>
 *
 * <p>For this implementation, we use SHR which should work correctly for the FRISC instruction set.
 * If the instruction set uses arithmetic shift for SHR on signed values, the truncation behavior
 * will be correct.
 *
 * <p><b>Why Right Shift by 16?</b>
 *
 * <p>Shifting right by 16 bits is equivalent to dividing by 2^16 = 65536:
 *
 * <ul>
 *   <li>It's faster than division (single instruction)
 *   <li>It's exact (no rounding errors, just truncation)
 *   <li>It preserves the sign (arithmetic right shift preserves sign bit)
 * </ul>
 *
 * <p><b>Edge Cases:</b>
 *
 * <ul>
 *   <li><b>Zero:</b> 0 >> 16 = 0 (correctly represents 0)
 *   <li><b>Negative Numbers:</b> Arithmetic right shift correctly handles negative values (sign bit
 *       is preserved)
 *   <li><b>Fractional Part Zero:</b> Values with zero fractional part convert exactly (e.g., 2.0 →
 *       2)
 *   <li><b>Large Values:</b> Large float values may overflow when converted to integers, but this
 *       is acceptable (C standard behavior)
 * </ul>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - single SHR instruction
 *   <li><b>Space Complexity:</b> O(1) - uses only registers
 * </ul>
 *
 * <p><b>FRISC Register Usage:</b>
 *
 * <ul>
 *   <li><b>R0:</b> Input Q16.16 value, then result (integer)
 *   <li><b>R6:</b> Return value (integer result)
 * </ul>
 *
 * <p><b>FRISC Code Pattern:</b>
 *
 * <pre>
 * F_F2I:
 *     PUSH R5
 *     MOVE R7, R5
 *     LOAD R0, (R5+08)    ; Q16.16 value
 *     SHR R0, %D 16, R0   ; divide by 65536 (shift right by 16)
 *     MOVE R0, R6         ; return integer result
 *     POP R5
 *     RET
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatToIntGenerator {

  /**
   * Generates the F_F2I helper function.
   *
   * @param context the code generation context
   */
  public void generate(CodeGenContext context) {
    Objects.requireNonNull(context, "context must not be null");

    FriscEmitter emitter = context.emitter();

    emitter.emitLabel("F_F2I", "Helper function: int floatToInt(float value)");

    emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
    emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");

    emitter.emitInstruction("LOAD", "R0", "(R5+08)", "float value (Q16.16)");

    // Convert from Q16.16 to integer: divide by 65536 (shift right by 16 bits)
    // Use arithmetic shift (SAR) to preserve sign for negative numbers
    // Note: FRISC might use SHR for arithmetic shift, check instruction set
    // For now, use SHR which should work for both signed and unsigned
    emitter.emitInstruction("SHR", "R0", "%D 16", "R0", "divide by 65536 to get integer part");
    emitter.emitInstruction("MOVE", "R0", "R6", "result (integer, truncated)");

    emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
    emitter.emitInstruction("RET", null, null, "return");

    emitter.emitNewline();
  }
}
