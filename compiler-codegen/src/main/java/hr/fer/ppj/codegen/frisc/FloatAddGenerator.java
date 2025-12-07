package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_FADD helper function for Q16.16 fixed-point float addition.
 * 
 * <p>This function implements <b>Q16.16 fixed-point addition</b>, which is the
 * simplest of the float operations because addition is linear and preserves
 * the fixed-point scaling.
 * 
 * <p><b>Algorithm: Q16.16 Fixed-Point Addition</b>
 * 
 * <p>The mathematical basis:
 * <pre>
 * (a × 65536) + (b × 65536) = (a + b) × 65536
 * </pre>
 * 
 * <p>Since both operands are already scaled by 65536, we can simply add them
 * as signed integers. The result is automatically in Q16.16 format because
 * the scaling factor is preserved.
 * 
 * <p><b>Why This Works:</b>
 * 
 * <p>In fixed-point arithmetic, addition is straightforward because:
 * <ul>
 *   <li>Both operands have the same scaling factor (65536)</li>
 *   <li>Adding scaled values preserves the scaling: (a×s) + (b×s) = (a+b)×s</li>
 *   <li>No overflow handling needed (32-bit signed addition handles it correctly)</li>
 *   <li>The result is automatically in the correct format</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>
 * a = 1.5 in Q16.16 = 0x00018000 (1.5 × 65536 = 98304)
 * b = 2.5 in Q16.16 = 0x00028000 (2.5 × 65536 = 163840)
 * 
 * result = 0x00018000 + 0x00028000 = 0x00040000
 * 0x00040000 / 65536 = 4.0 ✓
 * </pre>
 * 
 * <p><b>Edge Cases:</b>
 * <ul>
 *   <li><b>Overflow:</b> 32-bit signed addition correctly handles overflow
 *       (wraps around, which is acceptable for fixed-point arithmetic)</li>
 *   <li><b>Negative Numbers:</b> Two's complement addition correctly handles
 *       negative operands</li>
 *   <li><b>Zero:</b> Adding zero works correctly (identity operation)</li>
 * </ul>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - single ADD instruction</li>
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
 * F_FADD:
 *     PUSH R5
 *     MOVE R7, R5
 *     LOAD R0, (R5+08)    ; a
 *     LOAD R1, (R5+0C)    ; b
 *     ADD R0, R1, R0      ; result = a + b
 *     MOVE R0, R6         ; return result
 *     POP R5
 *     RET
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatAddGenerator {
    
    /**
     * Generates the F_FADD helper function.
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_FADD", "Helper function: float add(float a, float b)");
        
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");
        
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (left operand, Q16.16)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (right operand, Q16.16)");
        
        // Q16.16 addition: just add the integers
        emitter.emitInstruction("ADD", "R0", "R1", "R0", "add Q16.16 values");
        emitter.emitInstruction("MOVE", "R0", "R6", "result (Q16.16)");
        
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        
        emitter.emitNewline();
    }
}

