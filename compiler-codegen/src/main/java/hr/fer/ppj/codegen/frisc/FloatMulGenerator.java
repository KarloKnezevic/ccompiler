package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates the F_FMUL helper function for Q16.16 fixed-point float multiplication.
 * 
 * <p>This function implements <b>Q16.16 fixed-point multiplication</b> using a <b>64-bit
 * signed product</b> computed via the Russian peasant (shift-and-add) algorithm with
 * HI:LO register pairs. The full 64-bit product is then shifted right by 16 bits to
 * obtain the Q16.16 result. This ensures correct results for all values in the safe
 * domain, including small decimal numbers like 1.5 × 2.5 = 3.75.
 * 
 * <h2>1. Q16.16 Representation</h2>
 * 
 * <p>In this compiler, the C type {@code float} is <b>not</b> IEEE-754. It is
 * implemented as a <b>signed 32-bit Q16.16 fixed-point value</b>:
 * <ul>
 *   <li>Internally stored as a 32-bit signed integer {@code raw}</li>
 *   <li>Real value: {@code value = raw / 65536.0} (i.e. {@code raw / 2^16})</li>
 *   <li>Bits 31-16: Integer part (signed 16-bit, range -32768 to 32767)</li>
 *   <li>Bits 15-0: Fractional part (unsigned 16-bit, scaled by 65536)</li>
 * </ul>
 * 
 * <p><b>Examples:</b>
 * <pre>
 * 0x00010000 = 1.0      (65536 / 65536 = 1.0)
 * 0x00018000 = 1.5      (98304 / 65536 = 1.5)
 * 0x00020000 = 2.0      (131072 / 65536 = 2.0)
 * 0xFFFE8000 = -1.5     (-98304 / 65536 = -1.5)
 * 0x00008000 = 0.5      (32768 / 65536 = 0.5)
 * </pre>
 * 
 * <p><b>Representable Range:</b>
 * <ul>
 *   <li>Minimum: {@code raw = -2^31} → {@code value = -2^31 / 2^16 = -32768.0}</li>
 *   <li>Maximum: {@code raw = 2^31 - 1} → {@code value ≈ (2^31 - 1) / 2^16 ≈ 32767.9999847}</li>
 * </ul>
 * 
 * <p>So in terms of what the user can write in C:
 * <blockquote>
 *   <b>Any C {@code float} between approximately -32768.0 and +32767.9999 is representable in Q16.16.</b>
 * </blockquote>
 * 
 * <p>The <b>resolution</b> (distance between two adjacent representable values) is:
 * <blockquote>
 *   {@code 1 / 65536 ≈ 0.0000152587890625}
 * </blockquote>
 * 
 * <h2>2. Semantic of F_FMUL</h2>
 * 
 * <p>Given two Q16.16 values:
 * <ul>
 *   <li>{@code raw_a} (32-bit signed) representing {@code A = raw_a / 65536.0}</li>
 *   <li>{@code raw_b} (32-bit signed) representing {@code B = raw_b / 65536.0}</li>
 * </ul>
 * 
 * <p>{@code F_FMUL} must compute the Q16.16 product:
 * <pre>
 * C = A * B
 * raw_result = ((int64) raw_a * (int64) raw_b) >> 16
 * </pre>
 * 
 * <p><b>Critical Requirement:</b> The multiplication {@code raw_a * raw_b} <b>must be
 * a full 64-bit signed product</b>, implemented with two 32-bit FRISC registers
 * {@code (HI : LO)}. This guarantees correct results for small decimal numbers like:
 * <ul>
 *   <li>{@code 1.5 × 2.5 = 3.75} → raw = 3.75 × 65536 = 245760</li>
 *   <li>{@code 1.5 × 2.5 × 3.5 = 13.125} → raw = 860160</li>
 * </ul>
 * 
 * <p><b>Why 64-bit Product is Necessary:</b>
 * 
 * <p>A naive implementation using only 32-bit arithmetic would compute:
 * <pre>
 * raw_result = (int32)(raw_a * raw_b) >> 16  // WRONG - overflows!
 * </pre>
 * 
 * <p>This is incorrect because:
 * <ul>
 *   <li>The product {@code raw_a * raw_b} can exceed 32 bits even for small values</li>
 *   <li>For example: {@code 1.5 × 2.5} in Q16.16 is {@code 98304 × 163840 = 16,106,127,360}</li>
 *   <li>This value (0x3C0000000) requires 34 bits, so a 32-bit multiply would wrap around</li>
 *   <li>After wrapping, shifting right by 16 produces an incorrect result</li>
 * </ul>
 * 
 * <p>By computing the <b>full 64-bit product</b> and then shifting right by 16 bits,
 * we ensure that the correct middle 32 bits are extracted, producing accurate Q16.16 results.
 * 
 * <h2>3. Algorithm Description</h2>
 * 
 * <p>The algorithm proceeds in the following steps:
 * 
 * <h3>Step 1: Load Arguments and Handle Sign</h3>
 * 
 * <ol>
 *   <li>Load raw {@code a} and {@code b} from the stack (Q16.16, signed 32-bit)</li>
 *   <li>If either operand is zero, return 0 immediately</li>
 *   <li>Initialize a sign register: {@code sign = +1}</li>
 *   <li>If {@code a < 0}: negate {@code a} and flip sign: {@code sign = -sign}</li>
 *   <li>If {@code b < 0}: negate {@code b} and flip sign: {@code sign = -sign}</li>
 *   <li>After this: {@code a_abs = |a|}, {@code b_abs = |b|}, {@code sign = +1 or -1}</li>
 * </ol>
 * 
 * <h3>Step 2: 64-bit Russian Peasant Multiplication</h3>
 * 
 * <p>We now multiply two <b>non-negative 32-bit integers</b> using the Russian peasant
 * algorithm, but with <b>64-bit accumulation</b> using HI:LO register pairs.
 * 
 * <p><b>Register Allocation:</b>
 * <ul>
 *   <li>{@code R0 = M_LO} (64-bit multiplicand low, initially {@code |a|})</li>
 *   <li>{@code R3 = M_HI} (64-bit multiplicand high, initially 0)</li>
 *   <li>{@code R1 = B} (multiplier, initially {@code |b|})</li>
 *   <li>{@code R2 = ACC_LO} (64-bit accumulator low)</li>
 *   <li>{@code R4 = ACC_HI} (64-bit accumulator high, sign saved on stack)</li>
 *   <li>{@code R6 = temp} (for bit checks and carry extraction)</li>
 * </ul>
 * 
 * <p><b>Initialization:</b>
 * <pre>
 * M_HI = 0
 * M_LO = |a|
 * ACC_HI = 0
 * ACC_LO = 0
 * B = |b|
 * </pre>
 * 
 * <p><b>Loop: while (B != 0)</b>
 * <ol>
 *   <li>If {@code (B & 1) != 0}: Perform 64-bit addition {@code ACC += M}:
 *       <ul>
 *         <li>{@code ACC_LO = ACC_LO + M_LO} (using {@code ADD})</li>
 *         <li>{@code ACC_HI = ACC_HI + M_HI + carry} (using {@code ADC})</li>
 *       </ul>
 *   </li>
 *   <li>Perform 64-bit left shift: {@code M <<= 1}:
 *       <ul>
 *         <li>Save old {@code M_LO}</li>
 *         <li>{@code M_LO <<= 1}</li>
 *         <li>Extract carry bit from old {@code M_LO} (bit 31): {@code carry = (old_M_LO >> 31) & 1}</li>
 *         <li>{@code M_HI <<= 1}</li>
 *         <li>{@code M_HI |= carry}</li>
 *       </ul>
 *   </li>
 *   <li>{@code B >>= 1} (logical right shift)</li>
 * </ol>
 * 
 * <p>After the loop: {@code (ACC_HI, ACC_LO) = 64-bit product = |a| * |b|}
 * 
 * <h3>Step 3: Q16.16 Scaling (Extract Middle 32 Bits)</h3>
 * 
 * <p>We have the 64-bit product in {@code (ACC_HI, ACC_LO)}. To convert to Q16.16,
 * we need to extract the middle 32 bits:
 * <pre>
 * raw_result_abs = (ACC_HI << 16) | (ACC_LO >> 16)
 * </pre>
 * 
 * <p>This is equivalent to shifting the 64-bit product right by 16 bits and taking
 * the lower 32 bits of the result.
 * 
 * <h3>Step 4: Apply Sign</h3>
 * 
 * <p>Restore the sign from the stack. If {@code sign < 0}, negate the result:
 * <pre>
 * if (sign < 0) {
 *     result = -result_abs
 * } else {
 *     result = result_abs
 * }
 * </pre>
 * 
 * <p>Return the result in register {@code R6}.
 * 
 * <h2>4. FRISC Instructions Used</h2>
 * 
 * <h3>Basic Arithmetic: ADD / SUB</h3>
 * 
 * <p>Used for:
 * <ul>
 *   <li>Adding/subtracting 32-bit words</li>
 *   <li>Negating operands (two's complement: {@code 0 - x})</li>
 *   <li>Incrementing/decrementing loop counters</li>
 * </ul>
 * 
 * <h3>Shifts: SHL / SHR</h3>
 * 
 * <p>Used for:
 * <ul>
 *   <li><b>Multiplicand doubling:</b> {@code M_LO <<= 1}, {@code M_HI <<= 1}</li>
 *   <li><b>Multiplier halving:</b> {@code B >>= 1}</li>
 *   <li><b>Carry extraction:</b> {@code (old_M_LO >> 31) & 1} to extract bit 31</li>
 *   <li><b>Q16.16 scaling:</b> {@code ACC_LO >> 16} and {@code ACC_HI << 16}</li>
 *   <li><b>Bit combination:</b> {@code OR} to combine shifted values</li>
 * </ul>
 * 
 * <h3>ADC (Add with Carry) - Critical for 64-bit Addition</h3>
 * 
 * <p><b>ADC</b> is a FRISC instruction that performs:
 * <pre>
 * R = X + Y + carry
 * </pre>
 * 
 * <p>where {@code carry} comes from the carry flag (C) set by a previous {@code ADD}
 * or {@code SUB} instruction. This is <b>essential</b> for correctly implementing
 * 64-bit addition using two 32-bit registers.
 * 
 * <p><b>Why ADC is Necessary:</b>
 * 
 * <p>When adding two 64-bit values represented as {@code (HI : LO)} pairs, we must:
 * <ol>
 *   <li>Add the low 32 bits first: {@code ACC_LO = ACC_LO + M_LO}</li>
 *   <li>This may produce a carry (overflow) if {@code ACC_LO + M_LO ≥ 2^32}</li>
 *   <li>The carry flag (C) is automatically set by the {@code ADD} instruction</li>
 *   <li>Add the high 32 bits with the carry: {@code ACC_HI = ACC_HI + M_HI + C}</li>
 * </ul>
 * 
 * <p><b>64-bit Addition Implementation:</b>
 * <pre>
 * ; Add low 32 bits
 * ADD  ACC_LO, M_LO, ACC_LO    ; ACC_LO = ACC_LO + M_LO (sets carry flag C)
 * 
 * ; Add high 32 bits with carry
 * ADC  ACC_HI, M_HI, ACC_HI    ; ACC_HI = ACC_HI + M_HI + C
 * </pre>
 * 
 * <p>This ensures correct propagation of the carry from the low word to the high word,
 * exactly as required in 64-bit arithmetic. Without {@code ADC}, the carry would be
 * lost, leading to incorrect results for products that exceed 32 bits.
 * 
 * <p><b>Example:</b>
 * <pre>
 * ACC = 0xFFFFFFFF:00000000  (high:low)
 * M   = 0x00000000:00000001
 * 
 * After ADD ACC_LO, M_LO, ACC_LO:
 *   ACC_LO = 0x00000001
 *   Carry flag C = 0 (no overflow)
 * 
 * After ADC ACC_HI, M_HI, ACC_HI:
 *   ACC_HI = 0xFFFFFFFF + 0x00000000 + 0 = 0xFFFFFFFF
 *   Result: 0xFFFFFFFF:00000001 ✓
 * 
 * But if ACC = 0xFFFFFFFF:FFFFFFFF and M = 0x00000000:00000001:
 * 
 * After ADD ACC_LO, M_LO, ACC_LO:
 *   ACC_LO = 0x00000000  (wraps around)
 *   Carry flag C = 1 (overflow occurred)
 * 
 * After ADC ACC_HI, M_HI, ACC_HI:
 *   ACC_HI = 0xFFFFFFFF + 0x00000000 + 1 = 0x00000000 (wraps, but carry was added)
 *   Result: 0x00000000:00000000 (incorrect due to 64-bit wrap, but carry was propagated) ✓
 * </pre>
 * 
 * <h3>SBC (Subtract with Borrow) - Not Used in F_FMUL</h3>
 * 
 * <p>{@code SBC} (Subtract with Borrow) is similar to {@code ADC} but for subtraction:
 * <pre>
 * R = X - Y - borrow
 * </pre>
 * 
 * <p>It is not used in {@code F_FMUL} (which only uses addition), but may be used in
 * other float operations like division for multiword subtraction.
 * 
 * <h2>5. Domain and Overflow Notes</h2>
 * 
 * <p><b>General Representable Range:</b>
 * <blockquote>
 *   {@code -32768.0 ≤ value ≤ +32767.9999847}
 * </blockquote>
 * 
 * <p><b>Safe Domain for Multiplication (no overflow in final 32-bit result):</b>
 * 
 * <p>To avoid overflow in the final 32-bit Q16.16 result, we need:
 * <pre>
 * |A * B| ≤ 32768
 * </pre>
 * 
 * <p>where {@code A} and {@code B} are the real C {@code float} values.
 * 
 * <p>For a <b>symmetric safe interval</b> {@code [-M, +M]} for each operand:
 * <pre>
 * M² ≤ 32768  ⇒  M ≈ 181.019...
 * </pre>
 * 
 * <p>Therefore:
 * <blockquote>
 *   <b>Safe domain for Q16.16 multiplication without overflow for any pair of operands:</b>
 *   <br>each operand in <b>[-181.0, +181.0]</b>.
 * </blockquote>
 * 
 * <p>Outside this range:
 * <ul>
 *   <li>Q16.16 can still represent the individual values</li>
 *   <li>But the product may overflow the 32-bit result and wrap around (two's complement)</li>
 * </ul>
 * 
 * <p><b>Note:</b> For typical operations (e.g., {@code 1.5 * 2.5 * 3.5}), the algorithm
 * is exact and overflow-free. The 64-bit product ensures correct results even when the
 * intermediate product exceeds 32 bits.
 * 
 * <h2>6. Complexity Analysis</h2>
 * 
 * <ul>
 *   <li><b>Time Complexity:</b> O(32) - the Russian peasant loop iterates at most 32 times
 *       (once per bit in the multiplier)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers (no dynamic memory allocation)</li>
 * </ul>
 * 
 * <h2>7. FRISC Register Usage</h2>
 * 
 * <ul>
 *   <li><b>R0:</b> M_LO (multiplicand low), then result after scaling</li>
 *   <li><b>R1:</b> B (multiplier), then ACC_HI during scaling</li>
 *   <li><b>R2:</b> ACC_LO (accumulator low)</li>
 *   <li><b>R3:</b> M_HI (multiplicand high), then temporary for negation</li>
 *   <li><b>R4:</b> Sign flag (+1 or -1), then ACC_HI (accumulator high)</li>
 *   <li><b>R5:</b> Frame pointer (preserved)</li>
 *   <li><b>R6:</b> Temporary during loop, then return value (Q16.16 result)</li>
 *   <li><b>R7:</b> Stack pointer (preserved)</li>
 *   <li><b>Stack:</b> Used to save sign flag during multiplication</li>
 * </ul>
 * 
 * <h2>8. Example: 1.5 × 2.5</h2>
 * 
 * <p><b>Input:</b>
 * <ul>
 *   <li>{@code a = 1.5} → {@code raw_a = 98304} (0x00018000)</li>
 *   <li>{@code b = 2.5} → {@code raw_b = 163840} (0x00028000)</li>
 * </ul>
 * 
 * <p><b>Computation:</b>
 * <ol>
 *   <li>Both operands are positive, so {@code sign = +1}, {@code a_abs = 98304}, {@code b_abs = 163840}</li>
 *   <li>64-bit multiplication: {@code 98304 × 163840 = 16,106,127,360} (0x3C0000000)</li>
 *   <li>64-bit product: {@code (HI, LO) = (0x00000003, 0xC0000000)}</li>
 *   <li>Extract middle 32 bits: {@code (0x00000003 << 16) | (0xC0000000 >> 16) = 0x0003C000 = 245760}</li>
 *   <li>Apply sign: {@code result = +245760}</li>
 * </ol>
 * 
 * <p><b>Output:</b>
 * <ul>
 *   <li>{@code raw_result = 245760} (0x0003C000)</li>
 *   <li>{@code value = 245760 / 65536 = 3.75} ✓</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatMulGenerator {
    
    /**
     * Generates the F_FMUL helper function.
     * 
     * <p>Calling convention:
     * <pre>
     *   push b (multiplier)
     *   push a (multiplicand)
     *   CALL F_FMUL
     *   ADD R7, %D 8, R7  ; cleanup arguments
     *   ; result in R6
     * </pre>
     * 
     * @param context the code generation context
     */
    public void generate(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_FMUL", "Helper function: float mul(float a, float b)");
        emitter.emitComment("F_FMUL: Q16.16 fixed-point multiplication using 32-bit Russian peasant algorithm");
        emitter.emitComment("Q16.16 representable range: [-32768.0, +32767.9999]");
        emitter.emitComment("Safe multiplication domain: [-181.0, +181.0] per operand (no overflow)");
        emitter.emitComment("Input:  a at (R5+08), b at (R5+0C)");
        emitter.emitComment("Output: R6 = a * b (Q16.16 format)");
        
        // Function prologue
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP");
        
        // Load arguments from stack
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (multiplicand, Q16.16)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (multiplier, Q16.16)");
        
        // Handle zero operands: if a == 0 or b == 0, return 0 immediately
        String ffmZeroLabel = context.labelGenerator().generateLabel();
        String ffmZeroDoneLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R0", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffmZeroLabel, "if a == 0, return 0");
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffmZeroLabel, "if b == 0, return 0");
        
        // Initialize sign = +1 (use R4 for sign)
        emitter.emitInstruction("MOVE", "%D 1", "R4", "sign = +1");
        
        // Handle sign of a: if (a < 0) { a = -a; sign = -sign; }
        String ffmADoneLabel = context.labelGenerator().generateLabel();
        String ffmANegLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R0", "%D 0", null);
        emitter.emitInstruction("JR_SGE", ffmADoneLabel, "a >= 0 → nothing");
        
        emitter.emitLabel(ffmANegLabel, "negate a");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R0", "R0", "R0 = -R0 (a_abs)");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R4", "R4", "sign = -sign");
        
        emitter.emitLabel(ffmADoneLabel, "a sign handled");
        
        // Handle sign of b: if (b < 0) { b = -b; sign = -sign; }
        String ffmBDoneLabel = context.labelGenerator().generateLabel();
        String ffmBNegLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_SGE", ffmBDoneLabel, "b >= 0 → nothing");
        
        emitter.emitLabel(ffmBNegLabel, "negate b");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R1", "R1", "R1 = -R1 (b_abs)");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R4", "R4", "sign = -sign");
        
        emitter.emitLabel(ffmBDoneLabel, "b sign handled");
        
        // Now R0 = |a|, R1 = |b|, R4 = sign (+1 or -1)
        // ============================================================
        // 64-bit Russian peasant multiplication using HI:LO registers
        // ============================================================
        // Compute full 64-bit product = |a| * |b| using two 32-bit registers
        // Register usage:
        // R0 = M_LO (64-bit multiplicand low, initially |a|)
        // R3 = M_HI (64-bit multiplicand high, initially 0)
        // R1 = B (multiplier, initially |b|)
        // R2 = ACC_LO (64-bit accumulator low)
        // R4 = ACC_HI (64-bit accumulator high) - sign saved on stack
        // R6 = temporary (for bit checks and carry extraction)
        
        emitter.emitComment("64-bit Russian peasant multiplication: product = |a| * |b|");
        
        // Save sign on stack (we'll use R4 for ACC_HI)
        emitter.emitInstruction("PUSH", "R4", null, "save sign on stack");
        
        // Initialize 64-bit multiplicand: M_LO = |a| (R0), M_HI = 0 (R3)
        emitter.emitInstruction("MOVE", "%D 0", "R3", "M_HI = 0 (high 32 bits of multiplicand)");
        // R0 already has |a|, so M_LO is already set
        
        // Initialize 64-bit accumulator: ACC_LO = 0, ACC_HI = 0
        emitter.emitInstruction("MOVE", "%D 0", "R2", "ACC_LO = 0");
        emitter.emitInstruction("MOVE", "%D 0", "R4", "ACC_HI = 0");
        
        // Russian peasant loop: while (B != 0)
        String ffmMulLoopLabel = context.labelGenerator().generateLabel();
        String ffmMulDoneLabel = context.labelGenerator().generateLabel();
        String ffmSkipAddLabel = context.labelGenerator().generateLabel();
        
        emitter.emitLabel(ffmMulLoopLabel, "64-bit multiplication loop");
        
        // Check if B == 0 (done)
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffmMulDoneLabel, "if B == 0, exit loop");
        
        // Check if the least significant bit of B is set: if (B & 1) ACC += M
        emitter.emitInstruction("AND", "R1", "%D 1", "R6", "R6 = B & 1");
        emitter.emitInstruction("CMP", "R6", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffmSkipAddLabel, "if (B & 1) == 0, skip add");
        
        // 64-bit ACC += M (add multiplicand to accumulator)
        // Add low 32 bits first
        emitter.emitInstruction("ADD", "R2", "R0", "R2", "ACC_LO += M_LO");
        // Add high 32 bits with carry using ADC
        emitter.emitInstruction("ADC", "R4", "R3", "R4", "ACC_HI += M_HI + carry");
        
        emitter.emitLabel(ffmSkipAddLabel, "skip add");
        
        // 64-bit M <<= 1 (shift multiplicand left by 1 bit)
        // Step 1: Save old M_LO before shifting
        emitter.emitInstruction("MOVE", "R0", "R6", "save old M_LO");
        
        // Step 2: Shift M_LO left by 1: M_LO <<= 1
        emitter.emitInstruction("SHL", "R0", "%D 1", "R0", "M_LO <<= 1");
        
        // Step 3: Extract the carry bit from old M_LO (bit 31)
        emitter.emitInstruction("SHR", "R6", "%D 31", "R6", "R6 = (old M_LO >> 31) & 1 (carry bit)");
        
        // Step 4: Shift M_HI left by 1: M_HI <<= 1
        emitter.emitInstruction("SHL", "R3", "%D 1", "R3", "M_HI <<= 1");
        
        // Step 5: OR the carry bit into M_HI: M_HI |= carry_from_LO
        emitter.emitInstruction("OR", "R3", "R6", "R3", "M_HI |= carry_from_LO");
        
        // B >>= 1 (logical right shift)
        emitter.emitInstruction("SHR", "R1", "%D 1", "R1", "B >>= 1");
        
        // Continue the loop
        emitter.emitInstruction("JR", ffmMulLoopLabel, "continue loop");
        
        emitter.emitLabel(ffmMulDoneLabel, "multiplication done");
        // After loop: (R4, R2) = 64-bit product = |a| * |b|
        // R4 = ACC_HI (high 32 bits), R2 = ACC_LO (low 32 bits)
        
        // ============================================================
        // Q16.16 scaling: extract middle 32 bits from 64-bit product
        // ============================================================
        // raw_result = (raw_a * raw_b) >> 16
        // We have 64-bit product in (R4, R2) = (ACC_HI, ACC_LO)
        // We need: result = (product >> 16)
        // This is: result = (R4 << 16) | (R2 >> 16)
        
        emitter.emitComment("Q16.16 scaling: extract middle 32 bits from 64-bit product");
        
        // Extract high 16 bits from R2 and low 16 bits from R4
        // result = (R4 << 16) | (R2 >> 16)
        emitter.emitInstruction("MOVE", "R2", "R0", "R0 = ACC_LO");
        emitter.emitInstruction("MOVE", "R4", "R1", "R1 = ACC_HI");
        emitter.emitInstruction("SHR", "R0", "%D 16", "R0", "R0 = ACC_LO >> 16 (low 16 bits of result)");
        emitter.emitInstruction("SHL", "R1", "%D 16", "R1", "R1 = ACC_HI << 16 (high 16 bits)");
        emitter.emitInstruction("OR", "R0", "R1", "R0", "result_abs = (ACC_HI << 16) | (ACC_LO >> 16)");
        
        // ============================================================
        // Apply sign to result
        // ============================================================
        
        emitter.emitComment("Apply sign to result");
        
        // Restore sign from stack and apply to result
        emitter.emitInstruction("POP", "R4", null, "restore sign from stack");
        
        // Apply sign: if sign < 0, negate result
        String ffmSignZeroLabel = context.labelGenerator().generateLabel();
        String ffmSignNegLabel = context.labelGenerator().generateLabel();
        String ffmSignDoneLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R4", "%D 0", null);
        emitter.emitInstruction("JR_EQ", ffmSignZeroLabel, "if sign == 0, result is 0");
        emitter.emitInstruction("JR_SLT", ffmSignNegLabel, "if sign < 0, negate");
        emitter.emitInstruction("JP", ffmSignDoneLabel, "sign > 0, no change");
        
        emitter.emitLabel(ffmSignZeroLabel, "result is zero");
        emitter.emitInstruction("MOVE", "%D 0", "R0", "R0 = 0");
        emitter.emitInstruction("JP", ffmSignDoneLabel, "jump to done");
        
        emitter.emitLabel(ffmSignNegLabel, "negate result");
        emitter.emitInstruction("MOVE", "%D 0", "R3", null);
        emitter.emitInstruction("SUB", "R3", "R0", "R0", "R0 = -R0");
        
        emitter.emitLabel(ffmSignDoneLabel, "sign applied");
        
        // Move result to return register
        emitter.emitInstruction("MOVE", "R0", "R6", "return value in R6");
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        emitter.emitInstruction("JP", ffmZeroDoneLabel, "skip zero handler");
        
        // Handle zero operands: return 0
        emitter.emitLabel(ffmZeroLabel, "zero operand");
        emitter.emitInstruction("MOVE", "%D 0", "R6", "return 0 for zero operand");
        emitter.emitInstruction("POP", "R5", null, "restore frame pointer");
        emitter.emitInstruction("RET", null, null, "return");
        
        emitter.emitLabel(ffmZeroDoneLabel, "end of function");
        emitter.emitNewline();
    }
}
