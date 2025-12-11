package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_FCMP helper function for Q16.16 fixed-point float comparison.
 *
 * <p>This function implements <b>Q16.16 fixed-point comparison</b>, which returns a three-way
 * comparison result following the standard C comparison convention.
 *
 * <p><b>Algorithm: Q16.16 Fixed-Point Comparison</b>
 *
 * <p>The function returns:
 *
 * <ul>
 *   <li><b>-1</b> if a < b (a is less than b)
 *   <li><b>0</b> if a == b (a is equal to b)
 *   <li><b>1</b> if a > b (a is greater than b)
 * </ul>
 *
 * <p><b>Why Integer Comparison Works:</b>
 *
 * <p>Since Q16.16 values are stored as signed integers with a positive scaling factor (65536), the
 * integer ordering is preserved:
 *
 * <ul>
 *   <li>If a < b as floats, then (a × 65536) < (b × 65536) as integers
 *   <li>If a == b as floats, then (a × 65536) == (b × 65536) as integers
 *   <li>If a > b as floats, then (a × 65536) > (b × 65536) as integers
 * </ul>
 *
 * <p>This is because multiplication by a positive constant preserves ordering. Therefore, we can
 * compare Q16.16 values directly using signed integer comparison.
 *
 * <p><b>Mathematical Proof:</b>
 *
 * <p>For any two real numbers a and b:
 *
 * <ul>
 *   <li>If a < b, then a × 65536 < b × 65536 (since 65536 > 0)
 *   <li>If a == b, then a × 65536 == b × 65536
 *   <li>If a > b, then a × 65536 > b × 65536
 * </ul>
 *
 * <p>Therefore, integer comparison of the Q16.16 representations correctly reflects the ordering of
 * the original float values.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * a = 1.5 in Q16.16 = 0x00018000
 * b = 2.5 in Q16.16 = 0x00028000
 *
 * CMP 0x00018000, 0x00028000  → a < b → return -1 ✓
 *
 * a = -1.5 in Q16.16 = 0xFFFE8000 (two's complement)
 * b = 1.5 in Q16.16 = 0x00018000
 *
 * CMP 0xFFFE8000, 0x00018000  → a < b → return -1 ✓
 * </pre>
 *
 * <p><b>Edge Cases:</b>
 *
 * <ul>
 *   <li><b>Negative Numbers:</b> Two's complement representation correctly handles negative
 *       comparisons (negative numbers are less than positive)
 *   <li><b>Zero:</b> Zero comparison works correctly (0 == 0)
 *   <li><b>Equal Values:</b> Equal comparison correctly returns 0
 *   <li><b>Overflow Values:</b> Integer comparison handles overflow values correctly (though they
 *       may not represent valid floats)
 * </ul>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - single CMP instruction plus conditional jumps
 *   <li><b>Space Complexity:</b> O(1) - uses only registers
 * </ul>
 *
 * <p><b>FRISC Register Usage:</b>
 *
 * <ul>
 *   <li><b>R0:</b> Operand a
 *   <li><b>R1:</b> Operand b
 *   <li><b>R6:</b> Return value (-1, 0, or 1)
 * </ul>
 *
 * <p><b>FRISC Code Pattern:</b>
 *
 * <pre>
 * F_FCMP:
 *     PUSH R5
 *     MOVE R7, R5
 *     LOAD R0, (R5+08)    ; a
 *     LOAD R1, (R5+0C)    ; b
 *     CMP R0, R1          ; compare a and b
 *     JP_SLT L_LESS       ; if a < b, return -1
 *     JP_SGT L_GREATER    ; if a > b, return 1
 *     MOVE %D 0, R6       ; a == b, return 0
 *     JP L_END
 * L_LESS:
 *     MOVE %D -1, R6      ; return -1
 *     JP L_END
 * L_GREATER:
 *     MOVE %D 1, R6       ; return 1
 * L_END:
 *     POP R5
 *     RET
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatCmpGenerator {

  /**
   * Generates the F_FCMP helper function.
   *
   * @param context the code generation context
   */
  public void generate(CodeGenContext context) {
    Objects.requireNonNull(context, "context must not be null");

    FriscEmitter emitter = context.emitter();

    emitter.emitLabel("F_FCMP", "Helper function: int cmp(float a, float b)");

    emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
    emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");

    emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (Q16.16)");
    emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (Q16.16)");

    // Compare Q16.16 values as integers (ordering is preserved)
    emitter.emitInstruction("CMP", "R0", "R1", null);

    String lessLabel = context.labelGenerator().generateLabel();
    String greaterLabel = context.labelGenerator().generateLabel();
    String endLabel = context.labelGenerator().generateLabel();

    emitter.emitInstruction("JP_SLT", lessLabel, "if a < b");
    emitter.emitInstruction("JP_SGT", greaterLabel, "if a > b");

    // Equal case
    emitter.emitInstruction("MOVE", "%D 0", "R6", "result = 0 (equal)");
    emitter.emitInstruction("JP", endLabel, null);

    emitter.emitLabel(lessLabel);
    emitter.emitInstruction("MOVE", "%D -1", "R6", "result = -1 (less)");
    emitter.emitInstruction("JP", endLabel, null);

    emitter.emitLabel(greaterLabel);
    emitter.emitInstruction("MOVE", "%D 1", "R6", "result = 1 (greater)");

    emitter.emitLabel(endLabel);
    emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
    emitter.emitInstruction("RET", null, null, "return");

    emitter.emitNewline();
  }
}
