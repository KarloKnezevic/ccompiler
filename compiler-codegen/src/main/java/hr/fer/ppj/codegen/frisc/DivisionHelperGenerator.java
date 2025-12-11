package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_DIV helper function for signed 32-bit integer division.
 *
 * <p>FRISC architecture does not have a native DIV instruction, so division is implemented using
 * the <b>binary long division algorithm</b> (also known as the <b>shift-subtract algorithm</b> or
 * <b>restoring division</b>).
 *
 * <p><b>Algorithm: Binary Long Division (Shift-Subtract Algorithm)</b>
 *
 * <p>This algorithm performs division by processing the dividend bit-by-bit, similar to long
 * division by hand. The algorithm works as follows:
 *
 * <ol>
 *   <li><b>Sign Handling:</b> Convert both operands to positive, track the sign of the result using
 *       XOR (negative ÷ negative = positive, etc.)
 *   <li><b>Initialization:</b> Set quotient (q) = 0, remainder (r) = 0
 *   <li><b>Binary Loop:</b> For each bit position i from 31 down to 0:
 *       <ul>
 *         <li>Shift remainder left by 1: r <<= 1
 *         <li>Bring down the i-th bit of the dividend into the remainder: r |= (dividend >> i) & 1
 *         <li>If remainder >= divisor:
 *             <ul>
 *               <li>Subtract divisor from remainder: r -= divisor
 *               <li>Set the i-th bit of the quotient: q |= (1 << i)
 *             </ul>
 *       </ul>
 *   <li><b>Sign Application:</b> If the original sign was negative, negate the quotient
 * </ol>
 *
 * <p><b>Mathematical Basis:</b>
 *
 * <p>The algorithm is based on the binary representation of numbers and works similarly to long
 * division in base 10. For example, dividing 91 ÷ 7:
 *
 * <pre>
 * 91 = 1011011₂ (binary)
 * 7  = 111₂ (binary)
 *
 * Step-by-step:
 * r = 0, q = 0
 *
 * i=6: r = 1, r < 7, q = 0
 * i=5: r = 10, r < 7, q = 0
 * i=4: r = 101 = 5, r < 7, q = 0
 * i=3: r = 1011 = 11, r >= 7, r = 11-7 = 4, q = 1000 = 8
 * i=2: r = 1001 = 9, r >= 7, r = 9-7 = 2, q = 1100 = 12
 * i=1: r = 101 = 5, r < 7, q = 1100 = 12
 * i=0: r = 1011 = 11, r >= 7, r = 11-7 = 4, q = 1101 = 13
 *
 * Result: q = 13, r = 4
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(32) for 32-bit integers. The loop iterates exactly 32 times (once
 *       per bit position).
 *   <li><b>Space Complexity:</b> O(1) - uses only a few registers
 *   <li><b>Comparison with Naive Algorithm:</b> The naive algorithm (repeated subtraction) would be
 *       O(dividend/divisor), which can be very large. This algorithm is much more efficient.
 * </ul>
 *
 * <p><b>Edge Cases Handled:</b>
 *
 * <ul>
 *   <li><b>Division by Zero:</b> If divisor == 0, return 0 (C standard says division by zero is
 *       undefined behavior, but we handle it gracefully)
 *   <li><b>Negative Operands:</b> Convert to positive, track sign, apply sign to quotient (C
 *       standard: truncation towards zero)
 *   <li><b>Truncation Towards Zero:</b> The algorithm implements C's truncation semantics: -7/3 =
 *       -2, 7/-3 = -2 (not -3)
 * </ul>
 *
 * <p><b>FRISC Register Usage:</b>
 *
 * <ul>
 *   <li><b>R0:</b> Quotient (q), being built bit-by-bit
 *   <li><b>R1:</b> Divisor (b), constant during the loop
 *   <li><b>R2:</b> Remainder (r), updated each iteration
 *   <li><b>R3:</b> Loop counter (i), counts from 31 down to 0
 *   <li><b>R4:</b> Sign flag (0 = positive, 1 = negative), also used to store dividend copy during
 *       loop
 *   <li><b>R5:</b> Temporary (used for bit extraction and bit setting)
 *   <li><b>R6:</b> Return value (final quotient)
 * </ul>
 *
 * <p><b>FRISC Code Pattern:</b>
 *
 * <pre>
 * F_DIV:
 *     ; Prologue: save R5, set frame pointer
 *     ; Load arguments: a from (R5+08), b from (R5+0C)
 *
 *     ; Handle division by zero
 *     CMP R1, %D 0
 *     JR_EQ L_ZERO          ; if b == 0, return 0
 *
 *     ; Sign handling: convert to positive, track sign
 *     ; ... (negate a if negative, negate b if negative, XOR sign) ...
 *
 *     ; Binary division loop
 *     MOVE R0, R4           ; save dividend
 *     MOVE %D 0, R0         ; q = 0
 *     MOVE %D 0, R2         ; r = 0
 *     MOVE %D 31, R3        ; i = 31
 * L_LOOP:
 *     CMP R3, %D 0          ; if i < 0, done
 *     JR_SLT L_DONE
 *
 *     SHL R2, %D 1, R2      ; r <<= 1
 *     ; Bring down i-th bit of dividend
 *     MOVE R4, R5           ; temp = dividend
 *     SHR R5, R3, R5        ; temp = dividend >> i
 *     AND R5, %D 1, R5      ; temp = (dividend >> i) & 1
 *     OR R2, R5, R2         ; r |= (dividend >> i) & 1
 *
 *     CMP R2, R1            ; if r >= b
 *     JR_SLT L_SKIP
 *     SUB R2, R1, R2        ; r -= b
 *     MOVE %D 1, R5         ; set bit i in quotient
 *     SHL R5, R3, R5        ; R5 = 1 << i
 *     OR R0, R5, R0         ; q |= (1 << i)
 * L_SKIP:
 *     SUB R3, %D 1, R3      ; i--
 *     JR L_LOOP
 *
 * L_DONE:
 *     ; Apply sign and return
 * </pre>
 *
 * <p><b>Why This Algorithm?</b>
 *
 * <ul>
 *   <li><b>Efficiency:</b> O(log n) instead of O(n) for naive repeated subtraction
 *   <li><b>Correctness:</b> Handles all edge cases (zero, negative, truncation)
 *   <li><b>Simplicity:</b> Easy to implement in assembly with basic shift and subtract
 *   <li><b>No Hardware Dependency:</b> Works on any architecture with shift and subtract
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class DivisionHelperGenerator {

  /**
   * Generates the F_DIV helper function.
   *
   * <p>Calling convention:
   *
   * <pre>
   *   push b (divisor)
   *   push a (dividend)
   *   CALL F_DIV
   *   ADD R7, %D 8, R7  ; cleanup arguments
   *   ; result in R6
   * </pre>
   *
   * @param context the code generation context
   */
  public void generate(CodeGenContext context) {
    Objects.requireNonNull(context, "context must not be null");

    FriscEmitter emitter = context.emitter();

    emitter.emitLabel("F_DIV", "Helper function: int div(int a, int b)");
    emitter.emitComment("F_DIV: signed 32-bit integer division (truncation towards zero)");
    emitter.emitComment("Input:  a at (R5+08), b at (R5+0C)");
    emitter.emitComment("Output: R6 = a / b");

    // Function prologue
    emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
    emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");

    // Load arguments from stack
    emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (dividend, pushed last, first parameter)");
    emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (divisor, pushed first, second parameter)");

    // Handle division by zero - return 0
    String divByZeroLabel = context.labelGenerator().generateLabel();
    String divEndLabel = context.labelGenerator().generateLabel();
    emitter.emitInstruction("CMP", "R1", "%D 0", null);
    emitter.emitInstruction("JR_EQ", divByZeroLabel, "division by zero");

    // Compute sign flag and make a, b non-negative
    // Initialize sign flag: R4 = 0 (positive)
    emitter.emitInstruction("MOVE", "%D 0", "R4", "sign = 0 (positive)");

    // if (a < 0) { a = -a; sign ^= 1; }
    String divSkipNegA = context.labelGenerator().generateLabel();
    emitter.emitInstruction("CMP", "R0", "%D 0", null);
    emitter.emitInstruction("JR_SGE", divSkipNegA, "if a >= 0, skip negation");
    emitter.emitInstruction("MOVE", "%D 0", "R2", null);
    emitter.emitInstruction("SUB", "R2", "R0", "R0", "a = -a");
    emitter.emitInstruction("XOR", "R4", "%D 1", "R4", "sign ^= 1");

    emitter.emitLabel(divSkipNegA, "skip negate a");

    // if (b < 0) { b = -b; sign ^= 1; }
    String divSkipNegB = context.labelGenerator().generateLabel();
    emitter.emitInstruction("CMP", "R1", "%D 0", null);
    emitter.emitInstruction("JR_SGE", divSkipNegB, "if b >= 0, skip negation");
    emitter.emitInstruction("MOVE", "%D 0", "R2", null);
    emitter.emitInstruction("SUB", "R2", "R1", "R1", "b = -b");
    emitter.emitInstruction("XOR", "R4", "%D 1", "R4", "sign ^= 1");

    emitter.emitLabel(divSkipNegB, "skip negate b");

    // Now both R0 and R1 are positive, perform unsigned binary division.
    // This is the core of the binary long division algorithm: we process
    // the dividend bit-by-bit from most significant to least significant,
    // building the quotient and remainder simultaneously.
    emitter.emitComment("Binary long division: shift-subtract algorithm, O(32) steps");

    // Save sign flag on stack (we'll need R4 to store the dividend).
    // The sign flag will be restored later to apply the correct sign to the quotient.
    emitter.emitInstruction("PUSH", "R4", null, "save sign flag on stack");

    // Copy dividend to R4. We need to preserve the original dividend because
    // we'll be extracting bits from it in the loop, and R0 will be used for
    // the quotient.
    emitter.emitInstruction("MOVE", "R0", "R4", "copy dividend to R4 (we'll need it in loop)");

    // Initialize quotient and remainder to 0.
    // The quotient will be built bit-by-bit as we process each bit of the dividend.
    // The remainder tracks the current partial remainder during the division process.
    emitter.emitInstruction("MOVE", "%D 0", "R0", "q = 0 (quotient)");
    emitter.emitInstruction("MOVE", "%D 0", "R2", "r = 0 (remainder)");

    // Initialize loop counter to 31 (most significant bit position).
    // We'll process bits from position 31 down to 0, examining each bit
    // of the dividend to determine the corresponding bit of the quotient.
    emitter.emitInstruction("MOVE", "%D 31", "R3", "i = 31 (loop counter)");

    // Binary division loop: for (int i = 31; i >= 0; --i)
    // This loop processes each bit of the dividend from most significant
    // to least significant, similar to long division by hand.
    String divLoopLabel = context.labelGenerator().generateLabel();
    String divLoopEndLabel = context.labelGenerator().generateLabel();
    emitter.emitLabel(divLoopLabel, "binary division loop (i = 31 down to 0)");

    // Check if i < 0 (done).
    // After processing all 32 bits (i goes from 31 to 0), we're done.
    emitter.emitInstruction("CMP", "R3", "%D 0", null);
    emitter.emitInstruction("JR_SLT", divLoopEndLabel, "if i < 0, done");

    // Shift remainder left by 1: r <<= 1
    // This makes room for the next bit of the dividend. In long division,
    // this corresponds to "bringing down" the next digit.
    emitter.emitInstruction("SHL", "R2", "%D 1", "R2", "r <<= 1");

    // Bring down the i-th bit of the dividend into the remainder.
    // We extract bit i from the dividend and add it to the least significant
    // bit of the remainder. This is equivalent to: r |= (dividend >> i) & 1
    emitter.emitInstruction("MOVE", "R4", "R5", "temp = dividend");
    emitter.emitInstruction("SHR", "R5", "R3", "R5", "temp = dividend >> i");
    emitter.emitInstruction("AND", "R5", "%D 1", "R5", "temp = (dividend >> i) & 1");
    emitter.emitInstruction("OR", "R2", "R5", "R2", "r |= (dividend >> i) & 1");

    // Check if remainder >= divisor: if (r >= b) { r -= b; q |= (1 << i); }
    // If the remainder is large enough, we can subtract the divisor and
    // set the corresponding bit in the quotient. This is the core operation
    // of long division: "how many times does the divisor fit into the current remainder?"
    String divSkipSub = context.labelGenerator().generateLabel();
    emitter.emitInstruction("CMP", "R2", "R1", null);
    emitter.emitInstruction("JR_SLT", divSkipSub, "if r < b, skip subtraction");

    // Subtract divisor from remainder: r -= b
    // This corresponds to subtracting the divisor from the current partial
    // remainder in long division.
    emitter.emitInstruction("SUB", "R2", "R1", "R2", "r -= b");

    // Set the i-th bit in the quotient: q |= (1 << i)
    // Since we successfully subtracted the divisor, we set bit i of the quotient.
    // This bit represents that 2^i × divisor fits into the dividend at this position.
    emitter.emitInstruction("MOVE", "%D 1", "R5", null);
    emitter.emitInstruction("SHL", "R5", "R3", "R5", "R5 = 1 << i");
    emitter.emitInstruction("OR", "R0", "R5", "R0", "q |= (1 << i)");

    emitter.emitLabel(divSkipSub, "skip subtraction");

    // Decrement loop counter: i--
    // Move to the next bit position (from most significant to least significant).
    emitter.emitInstruction("SUB", "R3", "%D 1", "R3", "i--");
    emitter.emitInstruction("JR", divLoopLabel, "continue loop");

    // Apply sign and finalize result
    emitter.emitLabel(divLoopEndLabel, "loop done, apply sign");
    emitter.emitInstruction("POP", "R4", null, "restore sign flag");
    emitter.emitInstruction("CMP", "R4", "%D 0", null);
    emitter.emitInstruction("JR_EQ", divEndLabel, "if sign == 0, result is positive");

    // Negate result: q = -q
    emitter.emitInstruction("MOVE", "%D 0", "R2", null);
    emitter.emitInstruction("SUB", "R2", "R0", "R0", "q = -q");
    emitter.emitInstruction("JP", divEndLabel, "result ready");

    // Handle division by zero case
    emitter.emitLabel(divByZeroLabel, "division by zero");
    emitter.emitInstruction("MOVE", "%D 0", "R0", "result 0 for division by zero");

    // Function epilogue
    emitter.emitLabel(divEndLabel, "end division");
    emitter.emitInstruction("MOVE", "R0", "R6", "result");
    emitter.emitInstruction("POP", "R5", null, "restore old frame pointer");
    emitter.emitInstruction("RET", null, null, "return to caller");
    emitter.emitNewline();
  }
}
