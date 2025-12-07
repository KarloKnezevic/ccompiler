package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_MUL64 helper function for 64-bit unsigned integer multiplication.
 * 
 * <p>This function is used by float multiplication (F_FMUL) to compute the full 64-bit
 * product of two 32-bit Q16.16 fixed-point values. It implements <b>64-bit unsigned
 * multiplication</b> using an extended version of the binary shift-and-add algorithm.
 * 
 * <p><b>Algorithm: 64-bit Binary Shift-and-Add Multiplication</b>
 * 
 * <p>This algorithm extends the 32-bit Russian peasant algorithm to handle 64-bit
 * operands and results. The key challenge is managing 64-bit values using only 32-bit
 * registers and detecting carry/overflow between the high and low 32-bit halves.
 * 
 * <p><b>Algorithm Steps:</b>
 * <ol>
 *   <li><b>Initialization:</b>
 *       <ul>
 *         <li>Multiplicand M = (M_HI, M_LO) where M_HI = 0, M_LO = a</li>
 *         <li>Multiplier B = b (32-bit unsigned)</li>
 *         <li>Accumulator ACC = (ACC_HI, ACC_LO) = (0, 0)</li>
 *       </ul>
 *   </li>
 *   <li><b>Binary Loop:</b> While B != 0:
 *       <ul>
 *         <li>If least significant bit of B is set (B & 1 == 1):
 *             <ul>
 *               <li>Perform 64-bit addition: ACC += M</li>
 *               <li>Detect carry from low 32 bits to high 32 bits</li>
 *               <li>Add carry to ACC_HI if needed</li>
 *             </ul>
 *         </li>
 *         <li>Shift M left by 1 bit (64-bit shift):
 *             <ul>
 *               <li>M_LO <<= 1</li>
 *               <li>Extract carry bit from M_LO (bit 31)</li>
 *               <li>M_HI <<= 1, then M_HI |= carry</li>
 *             </ul>
 *         </li>
 *         <li>Shift B right by 1 bit: B >>= 1</li>
 *       </ul>
 *   </li>
 *   <li><b>Result:</b> Return (ACC_HI, ACC_LO) as (R1, R0)</li>
 * </ol>
 * 
 * <p><b>Why 64-bit Multiplication for Float Operations?</b>
 * 
 * <p>Q16.16 fixed-point multiplication requires 64-bit precision:
 * <pre>
 * (a × 65536) × (b × 65536) = (a × b) × 65536²
 * </pre>
 * 
 * <p>The product has 64 bits of precision (32 bits for integer part, 32 bits for
 * fractional part). We need the full 64-bit product to correctly convert back to
 * Q16.16 format by dividing by 65536.
 * 
 * <p><b>Carry Detection Algorithm:</b>
 * 
 * <p>When adding two 32-bit values, a carry occurs if the result is less than
 * either operand (unsigned comparison). This is because:
 * <ul>
 *   <li>If a + b < a (or a + b < b), then overflow occurred</li>
 *   <li>The carry bit is 1, which must be added to the high 32 bits</li>
 * </ul>
 * 
 * <p>Example:
 * <pre>
 * 0xFFFFFFFF + 1 = 0x00000000 (with carry)
 * 0xFFFFFFFF + 0 = 0xFFFFFFFF (no carry)
 * </pre>
 * 
 * <p><b>64-bit Left Shift Algorithm:</b>
 * 
 * <p>Shifting a 64-bit value left requires:
 * <ol>
 *   <li>Shift low 32 bits left: M_LO <<= 1</li>
 *   <li>Extract the bit that "falls off" from M_LO (bit 31)</li>
 *   <li>Shift high 32 bits left: M_HI <<= 1</li>
 *   <li>OR the extracted bit into the least significant bit of M_HI</li>
 * </ol>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(log₂|b|) ≈ O(32) for 32-bit multipliers.
 *       The loop iterates at most 32 times (once per bit in the multiplier).</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * <p><b>FRISC Register Usage:</b>
 * <ul>
 *   <li><b>R0:</b> Multiplicand low (M_LO), then result low (LO)</li>
 *   <li><b>R1:</b> Multiplicand high (M_HI), then result high (HI)</li>
 *   <li><b>R2:</b> Multiplier (B), shifted right each iteration</li>
 *   <li><b>R3:</b> Accumulator low (ACC_LO)</li>
 *   <li><b>R4:</b> Accumulator high (ACC_HI)</li>
 *   <li><b>R6:</b> Temporary (bit test, carry detection), then copy of LO</li>
 * </ul>
 * 
 * <p><b>Calling Convention:</b>
 * <pre>
 * push b (right operand, 32-bit unsigned)
 * push a (left operand, 32-bit unsigned)
 * CALL F_MUL64
 * ADD R7, %D 8, R7  ; cleanup arguments
 * ; Result: R1 = high 32 bits, R0 = low 32 bits, R6 = copy of R0
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class Mul64HelperGenerator {
    
    /**
     * Generates the F_MUL64 helper function.
     * 
     * <p>Calling convention:
     * <pre>
     *   push b (right operand)
     *   push a (left operand)
     *   CALL F_MUL64
     *   ADD R7, %D 8, R7  ; cleanup arguments
     *   ; result: R1 = high 32 bits, R0 = low 32 bits, R6 = copy of R0
     * </pre>
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();

        emitter.emitLabel("F_MUL64", "Helper function: 64-bit unsigned multiplication");
        emitter.emitComment("F_MUL64: 64-bit unsigned integer multiplication");
        emitter.emitComment("Input:  a at (R5+08), b at (R5+0C) (both assumed non-negative)");
        emitter.emitComment("Output: R1 = high 32 bits, R0 = low 32 bits, R6 = copy of R0");

        // Prologue
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");

        // Load arguments from stack
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (int32, unsigned)");
        emitter.emitInstruction("LOAD", "R2", "(R5+0C)", "b (int32, unsigned)");

        // Handle trivial case b == 0
        String mul64ContLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R2", "%D 0", null);
        emitter.emitInstruction("JR_NE", mul64ContLabel, "if b != 0, continue");
        emitter.emitInstruction("MOVE", "%D 0", "R0", "LO = 0");
        emitter.emitInstruction("MOVE", "%D 0", "R1", "HI = 0");
        emitter.emitInstruction("MOVE", "%D 0", "R6", "low 32 bits copy");
        emitter.emitInstruction("POP", "R5", null, "restore old frame pointer");
        emitter.emitInstruction("RET", null, null, "return to caller");

        emitter.emitLabel(mul64ContLabel, "continue multiplication");

        // Initialize 64-bit multiplicand and accumulator
        emitter.emitInstruction("MOVE", "%D 0", "R1", "M_HI = 0 (high 32 bits of multiplicand)");
        emitter.emitInstruction("MOVE", "%D 0", "R3", "ACC_LO = 0");
        emitter.emitInstruction("MOVE", "%D 0", "R4", "ACC_HI = 0");

        // Main loop: 64-bit Russian peasant algorithm (unsigned).
        // This loop extends the 32-bit multiplication algorithm to handle 64-bit
        // operands and results. The key challenge is managing carry propagation
        // between the high and low 32-bit halves.
        String mul64LoopLabel = context.labelGenerator().generateLabel();
        String mul64SkipAddLabel = context.labelGenerator().generateLabel();
        String mul64NoCarryLabel = context.labelGenerator().generateLabel();
        String mul64DoneLabel = context.labelGenerator().generateLabel();

        emitter.emitLabel(mul64LoopLabel, "64-bit multiplication loop");

        // Check if B == 0 (done).
        // After right-shifting B enough times, it becomes 0, indicating we've
        // processed all bits. This is the loop termination condition.
        emitter.emitInstruction("CMP", "R2", "%D 0", null);
        emitter.emitInstruction("JR_EQ", mul64DoneLabel, "if B == 0, exit loop");

        // Check if the least significant bit of B is set: if (B & 1) ACC += M
        // This is the core of the algorithm: if bit i of the multiplier is set,
        // we add 2^i × multiplicand to the accumulator.
        emitter.emitInstruction("AND", "R2", "%D 1", "R6", "R6 = B & 1");
        emitter.emitInstruction("CMP", "R6", "%D 0", null);
        emitter.emitInstruction("JR_EQ", mul64SkipAddLabel, "if (B & 1) == 0, skip add");

        // 64-bit ACC += M (add multiplicand to accumulator).
        // This requires adding two 64-bit values using only 32-bit operations.
        // We add the low 32 bits first, detect carry, then add the high 32 bits.
        
        // Step 1: Add low 32 bits: temp_lo = ACC_LO + M_LO
        // The result is stored in R6 temporarily so we can detect carry.
        emitter.emitInstruction("ADD", "R3", "R0", "R6", "R6 = ACC_LO + M_LO");

        // Step 2: Detect carry from low 32 bits.
        // Carry occurs if the result is less than the original ACC_LO (unsigned comparison).
        // This happens when ACC_LO + M_LO overflows the 32-bit range.
        // Example: 0xFFFFFFFF + 1 = 0x00000000 (with carry)
        emitter.emitInstruction("CMP", "R6", "R3", null);
        emitter.emitInstruction("JR_SGE", mul64NoCarryLabel, "if no carry, skip increment");
        
        // Step 3: If carry occurred, increment the high 32 bits.
        // The carry from the low 32-bit addition must be added to ACC_HI.
        emitter.emitInstruction("ADD", "R4", "%D 1", "R4", "ACC_HI += 1");

        emitter.emitLabel(mul64NoCarryLabel, "no carry");
        
        // Step 4: Store the low 32-bit result.
        // Move the computed low 32 bits from R6 to ACC_LO (R3).
        emitter.emitInstruction("MOVE", "R6", "R3", "ACC_LO = temp_lo");

        // Step 5: Add high 32 bits: ACC_HI = ACC_HI + M_HI
        // This completes the 64-bit addition. Note that we don't need to check
        // for carry here because M_HI starts at 0 and only grows, and we're
        // adding to ACC_HI which can accommodate the result.
        emitter.emitInstruction("ADD", "R4", "R1", "R4", "ACC_HI = ACC_HI + M_HI");

        emitter.emitLabel(mul64SkipAddLabel, "skip add");

        // 64-bit M <<= 1 (shift multiplicand left by 1 bit).
        // This requires shifting both the high and low 32-bit halves, and
        // propagating the carry from the low half to the high half.
        
        // Step 1: Save old M_LO before shifting (we need it to extract the carry bit).
        emitter.emitInstruction("MOVE", "R0", "R6", "save old M_LO");
        
        // Step 2: Shift M_LO left by 1: M_LO <<= 1
        emitter.emitInstruction("SHL", "R0", "%D 1", "R0", "M_LO <<= 1");
        
        // Step 3: Extract the carry bit from old M_LO.
        // The bit that "falls off" from M_LO (bit 31) becomes the carry bit
        // that must be added to the least significant bit of M_HI.
        emitter.emitInstruction("SHR", "R6", "%D 31", "R6", "R6 = (old M_LO >> 31) & 1 (carry bit)");
        
        // Step 4: Shift M_HI left by 1: M_HI <<= 1
        emitter.emitInstruction("SHL", "R1", "%D 1", "R1", "M_HI <<= 1");
        
        // Step 5: OR the carry bit into M_HI: M_HI |= carry_from_LO
        // This propagates the carry from the low half to the high half.
        emitter.emitInstruction("OR", "R1", "R6", "R1", "M_HI |= carry_from_LO");

        // B >>= 1 (logical right shift).
        // Move to the next bit of the multiplier.
        emitter.emitInstruction("SHR", "R2", "%D 1", "R2", "B >>= 1");

        // Continue the loop to process the next bit.
        emitter.emitInstruction("JR", mul64LoopLabel, "continue loop");

        // Output registers: R0 = LO, R1 = HI, R6 = copy of LO
        emitter.emitLabel(mul64DoneLabel, "multiplication done");
        emitter.emitInstruction("MOVE", "R3", "R0", "LO → R0");
        emitter.emitInstruction("MOVE", "R4", "R1", "HI → R1");
        emitter.emitInstruction("MOVE", "R0", "R6", "low 32 bits copy for compatibility");

        // Restore old frame pointer
        emitter.emitInstruction("POP", "R5", null, "restore old frame pointer");
        emitter.emitInstruction("RET", null, null, "return to caller");

        emitter.emitNewline();
    }
}

