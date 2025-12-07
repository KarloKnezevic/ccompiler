package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_FSUB helper function for Q16.16 fixed-point float subtraction.
 * 
 * <p>This function implements <b>Q16.16 fixed-point subtraction</b>, which is
 * similar to addition but uses subtraction instead. Like addition, subtraction
 * is linear and preserves the fixed-point scaling.
 * 
 * <p><b>Algorithm: Q16.16 Fixed-Point Subtraction</b>
 * 
 * <p>The mathematical basis:
 * <pre>
 * (a × 65536) - (b × 65536) = (a - b) × 65536
 * </pre>
 * 
 * <p>Since both operands are already scaled by 65536, we can simply subtract
 * them as signed integers. The result is automatically in Q16.16 format because
 * the scaling factor is preserved.
 * 
 * <p><b>Why This Works:</b>
 * 
 * <p>In fixed-point arithmetic, subtraction is straightforward because:
 * <ul>
 *   <li>Both operands have the same scaling factor (65536)</li>
 *   <li>Subtracting scaled values preserves the scaling: (a×s) - (b×s) = (a-b)×s</li>
 *   <li>No overflow handling needed (32-bit signed subtraction handles it correctly)</li>
 *   <li>The result is automatically in the correct format</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>
 * a = 3.5 in Q16.16 = 0x00038000 (3.5 × 65536 = 229376)
 * b = 1.5 in Q16.16 = 0x00018000 (1.5 × 65536 = 98304)
 * 
 * result = 0x00038000 - 0x00018000 = 0x00020000
 * 0x00020000 / 65536 = 2.0 ✓
 * </pre>
 * 
 * <p><b>Edge Cases:</b>
 * <ul>
 *   <li><b>Underflow:</b> 32-bit signed subtraction correctly handles underflow
 *       (wraps around, which is acceptable for fixed-point arithmetic)</li>
 *   <li><b>Negative Numbers:</b> Two's complement subtraction correctly handles
 *       negative operands and negative results</li>
 *   <li><b>Zero:</b> Subtracting zero works correctly (identity operation)</li>
 *   <li><b>Equal Operands:</b> Subtracting equal values correctly produces zero</li>
 * </ul>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - single SUB instruction</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * <p><b>FRISC Register Usage:</b>
 * <ul>
 *   <li><b>R0:</b> Operand a, then result</li>
 *   <li><b>R1:</b> Operand b</li>
 *   <li><b>R6:</b> Return value (Q16.16 result)</li>
 * </ul>
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * F_FSUB:
 *     PUSH R5
 *     MOVE R7, R5
 *     LOAD R0, (R5+08)    ; a
 *     LOAD R1, (R5+0C)    ; b
 *     SUB R0, R1, R0      ; result = a - b
 *     MOVE R0, R6         ; return result
 *     POP R5
 *     RET
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatSubGenerator {
    
    /**
     * Generates the F_FSUB helper function.
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_FSUB", "Helper function: float sub(float a, float b)");
        
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");
        
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (left operand, Q16.16)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (right operand, Q16.16)");
        
        // Q16.16 subtraction: just subtract the integers
        emitter.emitInstruction("SUB", "R0", "R1", "R0", "subtract Q16.16 values");
        emitter.emitInstruction("MOVE", "R0", "R6", "result (Q16.16)");
        
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        
        emitter.emitNewline();
    }
}

