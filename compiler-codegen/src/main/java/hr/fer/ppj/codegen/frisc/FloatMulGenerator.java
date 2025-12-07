package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_FMUL helper function for Q16.16 fixed-point float multiplication.
 * 
 * <p>This function implements <b>Q16.16 fixed-point multiplication</b> using 64-bit
 * integer arithmetic. Q16.16 is a fixed-point representation where:
 * <ul>
 *   <li>32-bit signed integer represents a fixed-point number</li>
 *   <li>16 high bits: integer part</li>
 *   <li>16 low bits: fractional part (scaled by 65536 = 2^16)</li>
 *   <li>Example: 0x00010000 = 1.0, 0x00018000 = 1.5</li>
 * </ul>
 * 
 * <p><b>Algorithm: Q16.16 Fixed-Point Multiplication</b>
 * 
 * <p>The mathematical basis:
 * <pre>
 * (a × 65536) × (b × 65536) = (a × b) × 65536²
 * </pre>
 * 
 * <p>To get the result in Q16.16 format, we need to divide by 65536:
 * <pre>
 * result = ((a × b) × 65536²) / 65536 = (a × b) × 65536
 * </pre>
 * 
 * <p>However, computing (a × b) × 65536 directly can overflow 32 bits. Instead, we:
 * <ol>
 *   <li><b>Compute Full 64-bit Product:</b> Use F_MUL64 to compute the full
 *       64-bit product P = |a| × |b|, which gives us (a × b) × 65536²</li>
 *   <li><b>Convert to Q16.16:</b> Extract the middle 32 bits from the 64-bit product:
 *       <pre>
 *       result = (LO >> 16) | (HI << 16)
 *       </pre>
 *       This effectively divides by 65536 while preserving precision.</li>
 * </ol>
 * 
 * <p><b>Algorithm Steps:</b>
 * <ol>
 *   <li><b>Sign Handling:</b>
 *       <ul>
 *         <li>Convert both operands to positive (magnitudes)</li>
 *         <li>Track the sign of the result using XOR (negative × negative = positive, etc.)</li>
 *       </ul>
 *   </li>
 *   <li><b>64-bit Multiplication:</b>
 *       <ul>
 *         <li>Call F_MUL64(|a|, |b|) to compute 64-bit product P = (HI, LO)</li>
 *         <li>F_MUL64 returns: R1 = HI (high 32 bits), R0 = LO (low 32 bits)</li>
 *       </ul>
 *   </li>
 *   <li><b>Sign Application:</b>
 *       <ul>
 *         <li>If result sign is negative, negate the 64-bit product using two's complement</li>
 *         <li>64-bit negation: ~(HI:LO) + 1, handling carry from low to high</li>
 *       </ul>
 *   </li>
 *   <li><b>Q16.16 Conversion:</b>
 *       <ul>
 *         <li>Extract middle 32 bits: result = (LO >> 16) | (HI << 16)</li>
 *         <li>This is equivalent to dividing the 64-bit product by 65536</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Why 64-bit Multiplication?</b>
 * 
 * <p>Multiplying two Q16.16 values directly can overflow:
 * <pre>
 * (a × 65536) × (b × 65536) = (a × b) × 65536²
 * </pre>
 * 
 * <p>For example, if a = b = 65536 (1.0 in Q16.16), the product is 65536²,
 * which requires more than 32 bits. We need the full 64-bit product to correctly
 * compute the Q16.16 result.
 * 
 * <p><b>Q16.16 Conversion Formula:</b>
 * 
 * <p>Given a 64-bit product P = (HI, LO) representing (a × b) × 65536²:
 * <pre>
 * result = (LO >> 16) | (HI << 16)
 * </pre>
 * 
 * <p>This extracts the middle 32 bits:
 * <ul>
 *   <li>Low 16 bits of HI become the high 16 bits of result (integer part)</li>
 *   <li>High 16 bits of LO become the low 16 bits of result (fractional part)</li>
 * </ul>
 * 
 * <p>Example:
 * <pre>
 * P = 0x0001000000000000 (1.0 × 1.0 = 1.0, scaled by 65536²)
 * HI = 0x00010000, LO = 0x00000000
 * result = (0x00000000 >> 16) | (0x00010000 << 16)
 *        = 0x00000000 | 0x00010000
 *        = 0x00010000 (1.0 in Q16.16)
 * </pre>
 * 
 * <p><b>64-bit Two's Complement Negation:</b>
 * 
 * <p>To negate a 64-bit value (HI, LO):
 * <ol>
 *   <li>Invert all bits: HI = ~HI, LO = ~LO</li>
 *   <li>Add 1: LO += 1</li>
 *   <li>If LO wrapped to 0, carry to HI: if LO == 0, HI += 1</li>
 * </ol>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(32) - dominated by F_MUL64 call</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * <p><b>FRISC Register Usage:</b>
 * <ul>
 *   <li><b>R0:</b> Operand a, then LO from F_MUL64, then temporary</li>
 *   <li><b>R1:</b> Operand b, then HI from F_MUL64, then temporary</li>
 *   <li><b>R2:</b> Temporary (sign negation, bit manipulation)</li>
 *   <li><b>R3:</b> Temporary (sign negation, bit manipulation)</li>
 *   <li><b>R4:</b> Sign flag (+1 or -1)</li>
 *   <li><b>R6:</b> Return value (Q16.16 result)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatMulGenerator {
    
    /**
     * Generates the F_FMUL helper function.
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_FMUL", "Helper function: float mul(float a, float b)");
        emitter.emitComment("F_FMUL: Q16.16 float multiplication with explicit sign handling");
        emitter.emitComment("Input:  a at (R5+08), b at (R5+0C) as signed Q16.16");
        emitter.emitComment("Output: R6 = signed Q16.16 result");
        
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");
        
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (Q16.16)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (Q16.16)");
        
        // Initialize sign = +1 (use R4 for sign)
        emitter.emitInstruction("MOVE", "%D 1", "R4", "sign = +1");
        
        // Handle sign of a: if (a < 0) { a = -a; sign = -sign; }
        String ffmADoneLabel = context.labelGenerator().generateLabel();
        String ffmANegLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("OR", "R0", "R0", "R2", "copy a into R2 and set flags");
        emitter.emitInstruction("JR_Z", ffmADoneLabel, "a == 0 → nothing");
        emitter.emitInstruction("JR_N", ffmANegLabel, "a < 0 → negate");
        emitter.emitInstruction("JP", ffmADoneLabel, "a > 0 → nothing");
        
        emitter.emitLabel(ffmANegLabel, "negate a");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R0", "R0", "R0 = -R0");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R4", "R4", "sign = -sign");
        
        emitter.emitLabel(ffmADoneLabel, "a sign handled");
        
        // Handle sign of b: if (b < 0) { b = -b; sign = -sign; }
        String ffmBDoneLabel = context.labelGenerator().generateLabel();
        String ffmBNegLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("OR", "R1", "R1", "R2", "copy b into R2 and set flags");
        emitter.emitInstruction("JR_Z", ffmBDoneLabel, "b == 0 → nothing");
        emitter.emitInstruction("JR_N", ffmBNegLabel, "b < 0 → negate");
        emitter.emitInstruction("JP", ffmBDoneLabel, "b > 0 → nothing");
        
        emitter.emitLabel(ffmBNegLabel, "negate b");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R1", "R1", "R1 = -R1");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R4", "R4", "sign = -sign");
        
        emitter.emitLabel(ffmBDoneLabel, "b sign handled");
        
        // Now R0 = |a|, R1 = |b|, R4 = sign (+1 or -1)
        // Save sign on stack before calling F_MUL64 (F_MUL64 uses R4 for ACC_HI)
        emitter.emitInstruction("PUSH", "R4", null, "save sign on stack");
        
        // Call unsigned F_MUL64(|a|, |b|)
        emitter.emitInstruction("PUSH", "R1", null, "push |b|");
        emitter.emitInstruction("PUSH", "R0", null, "push |a|");
        emitter.emitInstruction("CALL", "F_MUL64", null, "unsigned 64-bit product");
        emitter.emitInstruction("ADD", "R7", "%D 8", "R7", "clean up arguments");
        
        // After F_MUL64: R1 = HI, R0 = LO (magnitude product)
        // Restore sign from stack
        emitter.emitInstruction("POP", "R4", null, "restore sign from stack");
        
        // Apply result sign: if sign < 0, negate 64-bit product
        String ffmSignZeroLabel = context.labelGenerator().generateLabel();
        String ffmNegateLabel = context.labelGenerator().generateLabel();
        String ffmSignDoneLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R4", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffmSignZeroLabel, "sign == 0 → result 0");
        emitter.emitInstruction("JR_SLT", ffmNegateLabel, "sign < 0 → negate");
        emitter.emitInstruction("JP", ffmSignDoneLabel, "sign > 0 → no change");
        
        emitter.emitLabel(ffmSignZeroLabel, "sign zero");
        emitter.emitInstruction("MOVE", "%D 0", "R0", null);
        emitter.emitInstruction("MOVE", "%D 0", "R1", null);
        emitter.emitInstruction("JP", ffmSignDoneLabel, null);
        
        emitter.emitLabel(ffmNegateLabel, "negate 64-bit result");
        // Build -1 mask safely
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "%D 1", "R2", "R2 = -1 (0xFFFFFFFF)");
        // 64-bit two's complement negation
        emitter.emitInstruction("XOR", "R0", "R2", "R0", "LO = ~LO");
        emitter.emitInstruction("XOR", "R1", "R2", "R1", "HI = ~HI");
        emitter.emitInstruction("ADD", "R0", "%D 1", "R0", "LO += 1");
        String ffmNegDoneLoLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R0", "%D 0", null);
        emitter.emitInstruction("JR_NE", ffmNegDoneLoLabel, "if low != 0, no carry");
        emitter.emitInstruction("ADD", "R1", "%D 1", "R1", "carry to high if low wrapped");
        
        emitter.emitLabel(ffmNegDoneLoLabel, "negation done");
        
        emitter.emitLabel(ffmSignDoneLabel, "sign applied");
        
        // Now R1:R0 = signed 64-bit product (Q32.32)
        // Convert to Q16.16: result = (LO >> 16) | (HI << 16)
        emitter.emitInstruction("MOVE", "R0", "R2", "LO");
        emitter.emitInstruction("MOVE", "R1", "R3", "HI");
        emitter.emitInstruction("SHR", "R2", "%D 16", "R2", "LO >> 16");
        emitter.emitInstruction("SHL", "R3", "%D 16", "R3", "HI << 16");
        emitter.emitInstruction("OR", "R2", "R3", "R6", "R6 = (LO >> 16) | (HI << 16)");
        
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        
        emitter.emitNewline();
    }
}

