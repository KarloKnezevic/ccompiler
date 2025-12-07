package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_I2F helper function for integer to Q16.16 fixed-point float conversion.
 * 
 * <p>This function implements <b>integer to float conversion</b> by converting a
 * 32-bit signed integer to Q16.16 fixed-point format. This is used when an integer
 * value needs to be used in a float context (e.g., mixed arithmetic operations).
 * 
 * <p><b>Algorithm: Integer to Q16.16 Conversion</b>
 * 
 * <p>The conversion formula:
 * <pre>
 * float_value = integer_value × 65536
 * </pre>
 * 
 * <p>This is equivalent to shifting the integer left by 16 bits, which multiplies
 * by 2^16 = 65536. The result is a Q16.16 fixed-point value where the integer part
 * is the original integer value, and the fractional part is zero.
 * 
 * <p><b>Mathematical Basis:</b>
 * 
 * <p>In Q16.16 format, a value represents:
 * <pre>
 * actual_value = stored_integer / 65536.0
 * </pre>
 * 
 * <p>To convert an integer i to Q16.16:
 * <pre>
 * stored_integer = i × 65536
 * actual_value = (i × 65536) / 65536.0 = i
 * </pre>
 * 
 * <p>Therefore, multiplying by 65536 (shifting left by 16) correctly converts
 * the integer to Q16.16 format.
 * 
 * <p><b>Example:</b>
 * <pre>
 * i = 5 (integer)
 * result = 5 << 16 = 0x00050000
 * 0x00050000 / 65536 = 5.0 ✓
 * 
 * i = -3 (integer)
 * result = -3 << 16 = 0xFFFD0000 (two's complement)
 * 0xFFFD0000 / 65536 = -3.0 ✓
 * </pre>
 * 
 * <p><b>Why Left Shift by 16?</b>
 * 
 * <p>Shifting left by 16 bits is equivalent to multiplying by 2^16 = 65536:
 * <ul>
 *   <li>It's faster than multiplication (single instruction)</li>
 *   <li>It's exact (no rounding errors)</li>
 *   <li>It preserves the sign (arithmetic left shift preserves sign bit)</li>
 * </ul>
 * 
 * <p><b>Edge Cases:</b>
 * <ul>
 *   <li><b>Zero:</b> 0 << 16 = 0 (correctly represents 0.0)</li>
 *   <li><b>Negative Numbers:</b> Two's complement left shift correctly handles
 *       negative integers (sign bit is preserved)</li>
 *   <li><b>Large Integers:</b> Shifting large integers may cause overflow, but
 *       this is acceptable (the result still represents a valid float, though
 *       it may be outside the normal range)</li>
 * </ul>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - single SHL instruction</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * <p><b>FRISC Register Usage:</b>
 * <ul>
 *   <li><b>R0:</b> Input integer value, then result (Q16.16)</li>
 *   <li><b>R6:</b> Return value (Q16.16 result)</li>
 * </ul>
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * F_I2F:
 *     PUSH R5
 *     MOVE R7, R5
 *     LOAD R0, (R5+08)    ; integer value
 *     SHL R0, %D 16, R0   ; multiply by 65536 (shift left by 16)
 *     MOVE R0, R6         ; return Q16.16 result
 *     POP R5
 *     RET
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IntToFloatGenerator {
    
    /**
     * Generates the F_I2F helper function.
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_I2F", "Helper function: float intToFloat(int value)");
        
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");
        
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "integer value");
        
        // Convert to Q16.16: multiply by 65536 (shift left by 16 bits)
        emitter.emitInstruction("SHL", "R0", "%D 16", "R0", "multiply by 65536 to get Q16.16");
        emitter.emitInstruction("MOVE", "R0", "R6", "result (Q16.16)");
        
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        
        emitter.emitNewline();
    }
}

