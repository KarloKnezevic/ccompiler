package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_FDIV helper function for Q16.16 fixed-point float division.
 * 
 * <p>This function implements <b>Q16.16 fixed-point division</b> using only 32-bit
 * arithmetic and shifts. Q16.16 is a fixed-point representation where:
 * <ul>
 *   <li>32-bit signed integer represents a fixed-point number</li>
 *   <li>16 high bits: integer part</li>
 *   <li>16 low bits: fractional part (scaled by 65536 = 2^16)</li>
 * </ul>
 * 
 * <p><b>Algorithm: Q16.16 Fixed-Point Division</b>
 * 
 * <p>The mathematical basis:
 * <pre>
 * result = (a / b) in Q16.16 format
 * </pre>
 * 
 * <p>where a and b are Q16.16 values (32-bit signed integers representing
 * fixed-point numbers scaled by 65536).
 * 
 * <p><b>Algorithm Steps:</b>
 * <ol>
 *   <li><b>Sign Handling:</b>
 *       <ul>
 *         <li>Convert both operands to absolute values (magnitudes)</li>
 *         <li>Track the sign of the result: +1 if same signs, -1 if different signs</li>
 *         <li>If dividend is zero, return 0 immediately</li>
 *       </ul>
 *   </li>
 *   <li><b>Division by Zero Check:</b>
 *       <ul>
 *         <li>If divisor is zero, return 0 (safe behavior, no trap)</li>
 *       </ul>
 *   </li>
 *   <li><b>32-bit Integer Part Division:</b>
 *       <ul>
 *         <li>Perform unsigned 32-bit division: integer_part = a_abs / b_abs</li>
 *         <li>Compute remainder: remainder = a_abs % b_abs</li>
 *         <li>This uses a shift-subtract algorithm (binary long division)</li>
 *       </ul>
 *   </li>
 *   <li><b>16-Step Fractional Refinement Loop:</b>
 *       <ul>
 *         <li>For i = 0 to 15 (16 iterations):</li>
 *         <li>remainder <<= 1</li>
 *         <li>frac <<= 1</li>
 *         <li>if remainder >= b_abs: remainder -= b_abs; frac |= 1</li>
 *         <li>This computes the lower 16 bits (fractional part) of the Q16.16 result</li>
 *       </ul>
 *   </li>
 *   <li><b>Combine Parts:</b>
 *       <ul>
 *         <li>result_abs = (integer_part << 16) | (frac & 0xFFFF)</li>
 *       </ul>
 *   </li>
 *   <li><b>Sign Application:</b>
 *       <ul>
 *         <li>If sign is negative, negate result_abs</li>
 *         <li>If sign is zero (dividend was zero), result is already 0</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Why This Algorithm?</b>
 * 
 * <p>This algorithm avoids the need for 64-bit division by:
 * <ul>
 *   <li>First computing the integer part using 32-bit division</li>
 *   <li>Then computing the fractional part using a 16-iteration loop</li>
 *   <li>Combining both parts into the final Q16.16 result</li>
 * </ul>
 * 
 * <p>This is more efficient and simpler than 64-bit division for Q16.16 fixed-point arithmetic.
 * 
 * <p><b>Example:</b>
 * <pre>
 * a = 0x00050000 (5.0 in Q16.16 = 5.0 × 65536 = 327680)
 * b = 0x00020000 (2.0 in Q16.16 = 2.0 × 65536 = 131072)
 * 
 * integer_part = 327680 / 131072 = 2
 * remainder = 327680 % 131072 = 65536
 * 
 * Fractional loop (16 iterations):
 *   i=0: remainder=131072, frac=0, remainder >= b_abs? yes → remainder=0, frac=1
 *   i=1: remainder=0, frac=2, remainder < b_abs? yes → frac=2
 *   ...
 *   After 16 iterations: frac = 0x8000 (0.5 in Q16.16 fractional part)
 * 
 * result_abs = (2 << 16) | 0x8000 = 0x00028000
 * result = 0x00028000 (2.5 in Q16.16 = 2.5 × 65536 = 163840)
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(32) for integer division + O(16) for fractional loop = O(48)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * <p><b>FRISC Register Usage:</b>
 * <ul>
 *   <li><b>R0:</b> Dividend a (initially), then integer_part (quotient), then result</li>
 *   <li><b>R1:</b> Divisor b (initially), then b_abs (constant during division)</li>
 *   <li><b>R2:</b> Remainder (during integer division and fractional loop)</li>
 *   <li><b>R3:</b> Fractional accumulator (frac) during fractional loop</li>
 *   <li><b>R4:</b> Sign flag (+1, -1, or 0)</li>
 *   <li><b>R5:</b> Loop counter (i) for fractional loop</li>
 *   <li><b>R6:</b> Return value (Q16.16 result)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatDivGenerator {
    
    /**
     * Generates the F_FDIV helper function.
     * 
     * <p>Calling convention:
     * <pre>
     *   push b (divisor)
     *   push a (dividend)
     *   CALL F_FDIV
     *   ADD R7, %D 8, R7  ; cleanup arguments
     *   ; result in R6
     * </pre>
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_FDIV", "Helper function: float div(float a, float b)");
        emitter.emitComment("F_FDIV: Q16.16 fixed-point division using 32-bit arithmetic");
        emitter.emitComment("Input:  a at (R5+08), b at (R5+0C)");
        emitter.emitComment("Output: R6 = a / b (Q16.16 format)");
        
        // Function prologue
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");
        
        // Load arguments from stack
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (dividend, Q16.16)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (divisor, Q16.16)");
        
        // Handle division by zero: if b == 0, return 0
        String ffdDivByZeroLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffdDivByZeroLabel, "if b == 0, return 0");
        
        // Initialize sign = +1 (use R4 for sign)
        emitter.emitInstruction("MOVE", "%D 1", "R4", "sign = +1");
        
        // Handle sign of a: if (a < 0) { a = -a; sign = -sign; }
        // Also check if a == 0: if so, sign = 0
        String ffdADoneLabel = context.labelGenerator().generateLabel();
        String ffdANegLabel = context.labelGenerator().generateLabel();
        String ffdAZeroLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R0", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffdAZeroLabel, "a == 0 → sign = 0");
        emitter.emitInstruction("JR_SLT", ffdANegLabel, "a < 0 → negate");
        emitter.emitInstruction("JP", ffdADoneLabel, "a > 0 → nothing");
        
        emitter.emitLabel(ffdAZeroLabel, "a is zero");
        emitter.emitInstruction("MOVE", "%D 0", "R4", "sign = 0 (result will be 0)");
        emitter.emitInstruction("JP", ffdADoneLabel, null);
        
        emitter.emitLabel(ffdANegLabel, "negate a");
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "R0", "R0", "R0 = -R0 (a_abs)");
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "R4", "R4", "sign = -sign");
        
        emitter.emitLabel(ffdADoneLabel, "a sign handled");
        
        // Handle sign of b: if (b < 0) { b = -b; sign = -sign; }
        String ffdBDoneLabel = context.labelGenerator().generateLabel();
        String ffdBNegLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_SGE", ffdBDoneLabel, "b >= 0 → nothing");
        
        emitter.emitLabel(ffdBNegLabel, "negate b");
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "R1", "R1", "R1 = -R1 (b_abs)");
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "R4", "R4", "sign = -sign");
        
        emitter.emitLabel(ffdBDoneLabel, "b sign handled");
        
        // Now R0 = a_abs, R1 = b_abs, R4 = sign (+1, -1, or 0)
        // If sign == 0 (a was zero), return 0 immediately
        emitter.emitInstruction("CMP", "R4", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffdDivByZeroLabel, "if sign == 0, result is 0");
        
        // Save sign on stack (we'll need R4 temporarily during division)
        emitter.emitInstruction("PUSH", "R4", null, "save sign on stack");
        
        // ============================================================
        // 32-bit unsigned integer division: integer_part = a_abs / b_abs
        // ============================================================
        // This uses the binary long division algorithm (shift-subtract)
        // We'll compute: integer_part in R0, remainder in R2
        
        emitter.emitComment("32-bit unsigned division: integer_part = a_abs / b_abs");
        
        // Save dividend in R4 (we need R0 for quotient)
        emitter.emitInstruction("MOVE", "R0", "R4", "save dividend a_abs in R4");
        
        // Initialize quotient and remainder
        emitter.emitInstruction("MOVE", "%D 0", "R0", "integer_part = 0 (quotient)");
        emitter.emitInstruction("MOVE", "%D 0", "R2", "remainder = 0");
        
        // Initialize loop counter: i = 31 (process bits from MSB to LSB)
        emitter.emitInstruction("MOVE", "%D 31", "R3", "i = 31 (loop counter)");
        
        // Binary division loop: for (int i = 31; i >= 0; --i)
        String ffdIntLoopLabel = context.labelGenerator().generateLabel();
        String ffdIntLoopEndLabel = context.labelGenerator().generateLabel();
        emitter.emitLabel(ffdIntLoopLabel, "32-bit integer division loop");
        
        // Check if i < 0 (done)
        emitter.emitInstruction("CMP", "R3", "%D 0", null);
        emitter.emitInstruction("JR_SLT", ffdIntLoopEndLabel, "if i < 0, done");
        
        // Shift remainder left by 1: remainder <<= 1
        emitter.emitInstruction("SHL", "R2", "%D 1", "R2", "remainder <<= 1");
        
        // Bring down the i-th bit of dividend into remainder
        emitter.emitInstruction("MOVE", "R4", "R5", "temp = dividend");
        emitter.emitInstruction("SHR", "R5", "R3", "R5", "temp = dividend >> i");
        emitter.emitInstruction("AND", "R5", "%D 1", "R5", "temp = (dividend >> i) & 1");
        emitter.emitInstruction("OR", "R2", "R5", "R2", "remainder |= (dividend >> i) & 1");
        
        // Check if remainder >= divisor: if (remainder >= b_abs) { remainder -= b_abs; integer_part |= (1 << i); }
        String ffdIntSkipSub = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R2", "R1", null);
        emitter.emitInstruction("JR_SLT", ffdIntSkipSub, "if remainder < b_abs, skip subtraction");
        
        // Subtract divisor from remainder: remainder -= b_abs
        emitter.emitInstruction("SUB", "R2", "R1", "R2", "remainder -= b_abs");
        
        // Set the i-th bit in quotient: integer_part |= (1 << i)
        emitter.emitInstruction("MOVE", "%D 1", "R5", null);
        emitter.emitInstruction("SHL", "R5", "R3", "R5", "R5 = 1 << i");
        emitter.emitInstruction("OR", "R0", "R5", "R0", "integer_part |= (1 << i)");
        
        emitter.emitLabel(ffdIntSkipSub, "skip subtraction");
        
        // Decrement loop counter: i--
        emitter.emitInstruction("SUB", "R3", "%D 1", "R3", "i--");
        emitter.emitInstruction("JR", ffdIntLoopLabel, "continue loop");
        
        emitter.emitLabel(ffdIntLoopEndLabel, "integer division done");
        // After loop: R0 = integer_part, R2 = remainder
        
        // ============================================================
        // 16-step fractional refinement loop
        // ============================================================
        // Compute fractional part: frac (lower 16 bits of Q16.16 result)
        
        emitter.emitComment("16-step fractional refinement loop");
        
        // Initialize fractional accumulator: frac = 0
        emitter.emitInstruction("MOVE", "%D 0", "R3", "frac = 0 (fractional accumulator)");
        
        // Initialize loop counter: i = 0
        emitter.emitInstruction("MOVE", "%D 0", "R5", "i = 0 (loop counter)");
        
        // Fractional loop: for (int i = 0; i < 16; ++i)
        String ffdFracLoopLabel = context.labelGenerator().generateLabel();
        String ffdFracLoopEndLabel = context.labelGenerator().generateLabel();
        emitter.emitLabel(ffdFracLoopLabel, "fractional refinement loop");
        
        // Check if i >= 16 (done)
        emitter.emitInstruction("CMP", "R5", "%D 16", null);
        emitter.emitInstruction("JR_SGE", ffdFracLoopEndLabel, "if i >= 16, done");
        
        // Shift remainder left by 1: remainder <<= 1
        emitter.emitInstruction("SHL", "R2", "%D 1", "R2", "remainder <<= 1");
        
        // Shift fractional accumulator left by 1: frac <<= 1
        emitter.emitInstruction("SHL", "R3", "%D 1", "R3", "frac <<= 1");
        
        // Check if remainder >= divisor: if (remainder >= b_abs) { remainder -= b_abs; frac |= 1; }
        String ffdFracSkipSub = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R2", "R1", null);
        emitter.emitInstruction("JR_SLT", ffdFracSkipSub, "if remainder < b_abs, skip subtraction");
        
        // Subtract divisor from remainder: remainder -= b_abs
        emitter.emitInstruction("SUB", "R2", "R1", "R2", "remainder -= b_abs");
        
        // Set lowest bit of frac: frac |= 1
        emitter.emitInstruction("OR", "R3", "%D 1", "R3", "frac |= 1");
        
        emitter.emitLabel(ffdFracSkipSub, "skip subtraction");
        
        // Increment loop counter: i++
        emitter.emitInstruction("ADD", "R5", "%D 1", "R5", "i++");
        emitter.emitInstruction("JR", ffdFracLoopLabel, "continue loop");
        
        emitter.emitLabel(ffdFracLoopEndLabel, "fractional loop done");
        // After loop: R0 = integer_part, R3 = frac (lower 16 bits)
        
        // ============================================================
        // Combine integer and fractional parts into Q16.16 result
        // ============================================================
        
        emitter.emitComment("Combine integer_part and frac into Q16.16 result");
        
        // Shift integer_part left by 16: integer_part <<= 16
        emitter.emitInstruction("SHL", "R0", "%D 16", "R0", "integer_part <<= 16");
        
        // Mask frac to 16 bits (just in case, though it should already be 16 bits)
        // Note: We can use AND with 0xFFFF, but FRISC might not support 16-bit immediate
        // Since we only did 16 iterations, frac is already in the lower 16 bits, so we can skip masking
        
        // Combine: result_abs = integer_part | frac
        emitter.emitInstruction("OR", "R0", "R3", "R0", "result_abs = (integer_part << 16) | frac");
        
        // ============================================================
        // Apply sign to result
        // ============================================================
        
        emitter.emitComment("Apply sign to result");
        
        // Restore sign from stack
        emitter.emitInstruction("POP", "R4", null, "restore sign from stack");
        
        // Apply sign: if sign < 0, negate result
        String ffdSignZeroLabel = context.labelGenerator().generateLabel();
        String ffdSignNegLabel = context.labelGenerator().generateLabel();
        String ffdSignDoneLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R4", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffdSignZeroLabel, "sign == 0 → result 0");
        emitter.emitInstruction("JR_SLT", ffdSignNegLabel, "sign < 0 → negate");
        emitter.emitInstruction("JP", ffdSignDoneLabel, "sign > 0 → no change");
        
        emitter.emitLabel(ffdSignZeroLabel, "sign zero");
        emitter.emitInstruction("MOVE", "%D 0", "R0", null);
        emitter.emitInstruction("JP", ffdSignDoneLabel, null);
        
        emitter.emitLabel(ffdSignNegLabel, "negate result");
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "R0", "R0", "R0 = -R0");
        
        emitter.emitLabel(ffdSignDoneLabel, "sign applied");
        
        // Move result to return register
        emitter.emitInstruction("MOVE", "R0", "R6", "return value in R6");
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        
        // Handle division by zero: return 0
        emitter.emitLabel(ffdDivByZeroLabel, "division by zero or dividend is zero");
        emitter.emitInstruction("MOVE", "%D 0", "R6", "return 0 for division by zero or zero dividend");
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        emitter.emitNewline();
    }
}
