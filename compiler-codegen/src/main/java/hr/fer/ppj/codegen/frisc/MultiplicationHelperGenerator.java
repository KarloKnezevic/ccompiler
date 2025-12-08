package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_MUL helper function for signed 32-bit integer multiplication.
 * 
 * <p>FRISC architecture does not have a native MUL instruction, so multiplication
 * is implemented using the <b>binary shift-and-add algorithm</b> (also known as the
 * <b>Russian peasant algorithm</b> or <b>binary multiplication</b>).
 * 
 * <p><b>Algorithm: Binary Shift-and-Add Multiplication (Russian Peasant Algorithm)</b>
 * 
 * <p>This algorithm multiplies two integers by examining each bit of the multiplier
 * and conditionally adding shifted versions of the multiplicand. The algorithm works
 * as follows:
 * 
 * <ol>
 *   <li><b>Sign Handling:</b> Convert both operands to positive, track the sign of
 *       the result using XOR (negative × negative = positive, etc.)</li>
 *   <li><b>Initialization:</b> Set accumulator to 0</li>
 *   <li><b>Binary Loop:</b> While multiplier (b) is not zero:
 *       <ul>
 *         <li>If the least significant bit of b is set (b & 1 == 1), add the
 *             current value of a to the accumulator</li>
 *         <li>Shift a left by 1 bit (a <<= 1), effectively multiplying by 2</li>
 *         <li>Shift b right by 1 bit (b >>= 1), effectively dividing by 2</li>
 *       </ul>
 *   </li>
 *   <li><b>Sign Application:</b> If the original sign was negative, negate the result</li>
 * </ol>
 * 
 * <p><b>Mathematical Basis:</b>
 * 
 * <p>The algorithm is based on the binary representation of numbers. For example,
 * multiplying 13 × 7:
 * <pre>
 * 13 = 1101₂ (binary)
 * 7  = 0111₂ (binary)
 * 
 * 13 × 7 = 13 × (2² + 2¹ + 2⁰)
 *        = 13 × 4 + 13 × 2 + 13 × 1
 *        = 52 + 26 + 13
 *        = 91
 * </pre>
 * 
 * <p>The algorithm computes this by:
 * <ul>
 *   <li>When bit 0 of 7 is set: add 13 × 1 = 13</li>
 *   <li>When bit 1 of 7 is set: add 13 × 2 = 26</li>
 *   <li>When bit 2 of 7 is set: add 13 × 4 = 52</li>
 * </ul>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(log₂|b|) ≈ O(32) for 32-bit integers.
 *       The loop iterates at most 32 times (once per bit in the multiplier).</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only a few registers</li>
 *   <li><b>Comparison with Naive Algorithm:</b> The naive algorithm (repeated addition)
 *       would be O(|b|), which is O(2³²) in the worst case. This algorithm is
 *       exponentially faster.</li>
 * </ul>
 * 
 * <p><b>Edge Cases Handled:</b>
 * <ul>
 *   <li><b>Zero Multiplier:</b> If b == 0, return 0 immediately (early exit optimization)</li>
 *   <li><b>Negative Operands:</b> Convert to positive, track sign, apply sign to result</li>
 *   <li><b>Overflow:</b> 32-bit signed multiplication can overflow, but the algorithm
 *       correctly computes the low 32 bits of the product (C standard behavior)</li>
 * </ul>
 * 
 * <p><b>FRISC Register Usage:</b>
 * <ul>
 *   <li><b>R0:</b> Multiplicand (a), shifted left each iteration</li>
 *   <li><b>R1:</b> Multiplier (b), shifted right each iteration</li>
 *   <li><b>R2:</b> Accumulator (result being built)</li>
 *   <li><b>R3:</b> Temporary (used for bit test: b & 1)</li>
 *   <li><b>R4:</b> Sign flag (0 = positive, 1 = negative)</li>
 *   <li><b>R6:</b> Return value (final result)</li>
 * </ul>
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * F_MUL:
 *     ; Prologue: save R5, set frame pointer
 *     ; Load arguments: a from (R5+08), b from (R5+0C)
 *     
 *     ; Handle zero multiplier
 *     CMP R1, %D 0
 *     JR_EQ L_ZERO          ; if b == 0, return 0
 *     
 *     ; Sign handling: convert to positive, track sign
 *     ; ... (negate a if negative, negate b if negative, XOR sign) ...
 *     
 *     ; Binary multiplication loop
 *     MOVE %D 0, R2         ; acc = 0
 * L_LOOP:
 *     CMP R1, %D 0          ; if b == 0, done
 *     JR_EQ L_DONE
 *     
 *     AND R1, %D 1, R3     ; R3 = b & 1
 *     CMP R3, %D 0          ; if bit is set, add
 *     JR_EQ L_SKIP
 *     ADD R2, R0, R2        ; acc += a
 * L_SKIP:
 *     SHL R0, %D 1, R0      ; a <<= 1
 *     SHR R1, %D 1, R1      ; b >>= 1
 *     JR L_LOOP
 *     
 * L_DONE:
 *     ; Apply sign and return
 * </pre>
 * 
 * <p><b>Why This Algorithm?</b>
 * <ul>
 *   <li><b>Efficiency:</b> O(log n) instead of O(n) for naive repeated addition</li>
 *   <li><b>Simplicity:</b> Easy to implement in assembly with basic shift and add operations</li>
 *   <li><b>Correctness:</b> Handles all edge cases (zero, negative, overflow)</li>
 *   <li><b>No Hardware Dependency:</b> Works on any architecture with shift and add</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class MultiplicationHelperGenerator {
    
    /**
     * Generates the F_MUL helper function.
     * 
     * <p>Calling convention:
     * <pre>
     *   push b (right operand)
     *   push a (left operand)
     *   CALL F_MUL
     *   ADD R7, %D 8, R7  ; cleanup arguments
     *   ; result in R6
     * </pre>
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_MUL", "Helper function: int mul(int a, int b)");
        emitter.emitComment("F_MUL: signed 32-bit integer multiplication");
        emitter.emitComment("Input:  a at (R5+08), b at (R5+0C)");
        emitter.emitComment("Output: R6 = a * b");
        emitter.emitComment("Algorithm: binary shift-and-add (Russian peasant), O(32) steps");
        
        // Function prologue
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");
        
        // Load arguments from stack
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (left operand, pushed last, first parameter)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (right operand, pushed first, second parameter)");
        
        // Handle zero multiplier: if b == 0, return 0 immediately
        String mulZeroLabel = context.labelGenerator().generateLabel();
        String mulContLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_EQ", mulZeroLabel, "if b == 0, return 0");
        
        emitter.emitLabel(mulContLabel, "continue multiplication");
        
        // Compute sign flag and make a, b non-negative
        // Initialize sign flag: R4 = 0 (positive)
        emitter.emitInstruction("MOVE", "%D 0", "R4", "sign = 0 (positive)");
        
        // if (a < 0) { a = -a; sign ^= 1; }
        String mulSkipNegA = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R0", "%D 0", null);
        emitter.emitInstruction("JR_SGE", mulSkipNegA, "if a >= 0, skip negation");
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "R0", "R0", "a = -a");
        emitter.emitInstruction("XOR", "R4", "%D 1", "R4", "sign ^= 1");
        
        emitter.emitLabel(mulSkipNegA, "skip negate a");
        
        // if (b < 0) { b = -b; sign ^= 1; }
        String mulSkipNegB = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_SGE", mulSkipNegB, "if b >= 0, skip negation");
        emitter.emitInstruction("MOVE", "%D 0", "R2", null);
        emitter.emitInstruction("SUB", "R2", "R1", "R1", "b = -b");
        emitter.emitInstruction("XOR", "R4", "%D 1", "R4", "sign ^= 1");
        
        emitter.emitLabel(mulSkipNegB, "skip negate b");
        
        // Now both R0 and R1 are positive, perform unsigned binary multiplication.
        // This is the core of the Russian peasant algorithm: we examine each bit
        // of the multiplier and conditionally add shifted versions of the multiplicand.
        emitter.emitComment("Binary shift-and-add multiplication (Russian peasant algorithm)");
        
        // Save sign flag on stack (we'll need R4 for temporary operations).
        // The sign flag will be restored later to apply the correct sign to the result.
        emitter.emitInstruction("PUSH", "R4", null, "save sign flag on stack");
        
        // Initialize accumulator to 0. The accumulator will hold the running sum
        // of shifted multiplicands as we process each bit of the multiplier.
        emitter.emitInstruction("MOVE", "%D 0", "R2", "acc = 0 (accumulator)");
        
        // Binary multiplication loop: while (b != 0)
        // This loop processes each bit of the multiplier from least significant
        // to most significant. The loop terminates when all bits have been processed
        // (b becomes 0 after right-shifting).
        String mulLoopLabel = context.labelGenerator().generateLabel();
        String mulLoopEndLabel = context.labelGenerator().generateLabel();
        emitter.emitLabel(mulLoopLabel, "binary multiplication loop");
        
        // Check if b == 0 (done).
        // After right-shifting b enough times, it becomes 0, indicating we've
        // processed all bits. This is the loop termination condition.
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_EQ", mulLoopEndLabel, "if b == 0, exit loop");
        
        // Check if the least significant bit of b is set: if (b & 1) acc += a
        // This is the core of the algorithm: if bit i of the multiplier is set,
        // we add 2^i × multiplicand to the accumulator.
        emitter.emitInstruction("AND", "R1", "%D 1", "R3", "R3 = b & 1");
        String mulSkipAdd = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R3", "%D 0", null);
        emitter.emitInstruction("JR_EQ", mulSkipAdd, "if (b & 1) == 0, skip add");
        
        // Add the current value of a to the accumulator.
        // At iteration i, a has been left-shifted i times, so it represents
        // multiplicand × 2^i. Adding it to the accumulator contributes this
        // term to the final product.
        emitter.emitInstruction("ADD", "R2", "R0", "R2", "acc += a");
        
        emitter.emitLabel(mulSkipAdd, "skip add");
        
        // Prepare for next iteration: shift a left and b right.
        // Shifting a left by 1 multiplies it by 2, preparing it for the next
        // bit position. Shifting b right by 1 moves to the next bit.
        emitter.emitInstruction("SHL", "R0", "%D 1", "R0", "a <<= 1");
        emitter.emitInstruction("SHR", "R1", "%D 1", "R1", "b >>= 1");
        
        // Continue the loop to process the next bit.
        emitter.emitInstruction("JR", mulLoopLabel, "continue loop");
        
        // Apply sign and finalize result
        emitter.emitLabel(mulLoopEndLabel, "loop done, apply sign");
        emitter.emitInstruction("POP", "R4", null, "restore sign flag");
        String mulEndLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R4", "%D 0", null);
        emitter.emitInstruction("JR_EQ", mulEndLabel, "if sign == 0, result is positive");
        
        // Negate result: acc = -acc
        emitter.emitInstruction("MOVE", "%D 0", "R0", null);
        emitter.emitInstruction("SUB", "R0", "R2", "R2", "acc = -acc");
        emitter.emitInstruction("JP", mulEndLabel, "result ready");
        
        // Handle zero multiplier case
        emitter.emitLabel(mulZeroLabel, "zero multiplier");
        emitter.emitInstruction("MOVE", "%D 0", "R2", "result 0 for zero multiplier");
        
        // Function epilogue
        emitter.emitLabel(mulEndLabel, "end multiplication");
        emitter.emitInstruction("MOVE", "R2", "R6", "result");
        emitter.emitInstruction("POP", "R5", null, "restore old frame pointer");
        emitter.emitInstruction("RET", null, null, "return to caller");
        emitter.emitNewline();
    }
}

