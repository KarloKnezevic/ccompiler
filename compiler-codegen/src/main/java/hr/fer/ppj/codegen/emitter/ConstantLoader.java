package hr.fer.ppj.codegen.emitter;

import java.util.Objects;

/**
 * Generates FRISC assembly code to load integer constants into registers.
 *
 * <p>This class handles the complexity of loading 32-bit integer constants, which may or may not
 * fit in a 20-bit signed immediate. It implements the <b>constant loading algorithm</b> that
 * optimizes code generation based on the constant value.
 *
 * <p><b>Algorithm: Constant Loading</b>
 *
 * <p>This class implements a <b>two-strategy constant loading algorithm</b>:
 *
 * <ol>
 *   <li><b>Small Constants (20-bit immediate):</b>
 *       <ul>
 *         <li>If the constant fits in a 20-bit signed immediate (-524288 to 524287), use a single
 *             MOVE instruction
 *         <li>This is the most efficient case: one instruction, one cycle
 *       </ul>
 *   <li><b>Large Constants (32-bit values):</b>
 *       <ul>
 *         <li>If the constant doesn't fit in 20 bits, construct it from high and low 16-bit parts
 *         <li>Load the high 16 bits, shift left by 16, then add the low 16 bits
 *         <li>This requires 2-3 instructions depending on whether the low part is zero
 *       </ul>
 * </ol>
 *
 * <p><b>Why 20-bit Immediate?</b>
 *
 * <p>FRISC instructions use a 20-bit signed immediate field for constant operands. This means
 * constants in the range [-524288, 524287] can be encoded directly in the instruction, while larger
 * values require construction from multiple parts.
 *
 * <p><b>Large Constant Construction Algorithm:</b>
 *
 * <p>For constants that don't fit in 20 bits:
 *
 * <ol>
 *   <li><b>Split Constant:</b> Split the 32-bit value into high and low 16-bit parts:
 *       <ul>
 *         <li>High part: bits 31-16 (value >> 16)
 *         <li>Low part: bits 15-0 (value & 0xFFFF)
 *       </ul>
 *   <li><b>Sign Extension:</b> Sign-extend the high part as a signed 16-bit value to handle
 *       negative constants correctly
 *   <li><b>Load High Part:</b> Load the high part into the target register
 *   <li><b>Shift Left:</b> Shift the register left by 16 bits to make room for the low part
 *   <li><b>Add Low Part:</b> Add the low part to complete the constant (skipped if low part is zero
 *       for optimization)
 * </ol>
 *
 * <p><b>FRISC Code Examples:</b>
 *
 * <p>Small constant (fits in 20 bits):
 *
 * <pre>
 * MOVE %D 42, R0          ; Single instruction
 * </pre>
 *
 * <p>Large constant (requires construction):
 *
 * <pre>
 * MOVE %D 32768, R0       ; Load high part (0x8000 = 32768)
 * SHL R0, %D 16, R0       ; Shift left by 16 bits
 * ADD R0, %D 1, R0        ; Add low part (0x0001 = 1)
 *                         ; Result: 0x80000001 = 2147483649
 * </pre>
 *
 * <p>Large constant with zero low part (optimized):
 *
 * <pre>
 * MOVE %D 32768, R0       ; Load high part
 * SHL R0, %D 16, R0       ; Shift left by 16 bits
 *                         ; Low part is zero, so ADD is skipped
 *                         ; Result: 0x80000000 = 2147483648
 * </pre>
 *
 * <p><b>Optimization: Zero Low Part</b>
 *
 * <p>If the low 16 bits are zero, the ADD instruction is skipped. This optimization reduces code
 * size and execution time for constants that are multiples of 65536.
 *
 * <p><b>Complexity:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - constant number of instructions (1-3)
 *   <li><b>Space Complexity:</b> O(1) - no additional memory required
 * </ul>
 *
 * <p><b>Register Usage:</b>
 *
 * <ul>
 *   <li><b>Target Register:</b> The register specified by {@code targetRegister} (typically R0, R6,
 *       etc.)
 *   <li><b>No Other Registers:</b> The algorithm only uses the target register, making it
 *       register-efficient
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ConstantLoader {

  /** Minimum value for a 20-bit signed immediate (-2^19 = -524288). */
  private static final int IMMEDIATE_MIN = -524288;

  /** Maximum value for a 20-bit signed immediate (2^19 - 1 = 524287). */
  private static final int IMMEDIATE_MAX = 524287;

  /**
   * Generates code to load a 32-bit integer constant into a register.
   *
   * <p>This method implements the constant loading algorithm, choosing between a single MOVE
   * instruction (for small constants) or a multi-instruction sequence (for large constants).
   *
   * <p><b>Algorithm:</b>
   *
   * <ol>
   *   <li><b>Check Immediate Range:</b> If value fits in 20-bit signed immediate (-524288 to
   *       524287), use single MOVE instruction
   *   <li><b>Split Constant:</b> Otherwise, split into high and low 16-bit parts
   *   <li><b>Sign Extension:</b> Sign-extend high part as signed 16-bit value
   *   <li><b>Load and Shift:</b> Load high part, shift left by 16 bits
   *   <li><b>Add Low Part:</b> Add low part (skipped if zero for optimization)
   * </ol>
   *
   * <p><b>Preconditions:</b>
   *
   * <ul>
   *   <li>{@code emitter} must not be null
   *   <li>{@code targetRegister} must not be null
   *   <li>{@code value} is a valid 32-bit signed integer
   * </ul>
   *
   * <p><b>Postconditions:</b>
   *
   * <ul>
   *   <li>The target register contains the constant value
   *   <li>1-3 instructions have been emitted (depending on constant size)
   * </ul>
   *
   * <p><b>Side Effects:</b>
   *
   * <ul>
   *   <li>Emits FRISC instructions to the emitter buffer
   *   <li>Modifies the target register
   * </ul>
   *
   * @param emitter the emitter to use for generating instructions (must not be null)
   * @param value the 32-bit integer value to load
   * @param targetRegister the target register (e.g., "R0", "R6") (must not be null)
   * @param comment optional comment describing the constant (may be null)
   */
  public void emitLoadIntConstant(
      FriscEmitter emitter, int value, String targetRegister, String comment) {
    Objects.requireNonNull(emitter, "emitter must not be null");
    Objects.requireNonNull(targetRegister, "targetRegister must not be null");

    if (value >= IMMEDIATE_MIN && value <= IMMEDIATE_MAX) {
      // Strategy 1: Value fits in 20-bit signed immediate - use single MOVE.
      // This is the optimal case: one instruction, one cycle, minimal code size.
      // The immediate value is encoded directly in the instruction, so no
      // additional computation is needed.
      String commentText = comment != null ? comment : "load constant " + value;
      emitter.emitInstruction("MOVE", "%D " + value, targetRegister, commentText);
    } else {
      // Strategy 2: Value doesn't fit in 20 bits - construct from hi and lo parts.
      // We split the 32-bit value into two 16-bit parts:
      // - High part: bits 31-16 (value >> 16)
      // - Low part: bits 15-0 (value & 0xFFFF)
      // Then we construct the value as: value = hi * 2^16 + lo

      int hi = (value >> 16) & 0xFFFF;
      int lo = value & 0xFFFF;

      // Sign-extend hi as signed 16-bit for proper representation of negative constants.
      // If the high 16 bits represent a negative number (bit 15 is 1), we need to
      // sign-extend it to 32 bits to preserve the sign. This is done by casting
      // to short (16-bit signed) and then back to int (which sign-extends).
      short hiShort = (short) hi;
      int hiSigned = hiShort; // This will sign-extend to 32 bits

      // Emit construction sequence.
      // Step 1: Load the high part into the target register.
      // The high part is sign-extended, so it may be negative if the original
      // constant was negative.
      String baseComment = comment != null ? comment : "load constant " + value;
      emitter.emitInstruction("MOVE", "%D " + hiSigned, targetRegister, baseComment + " (hi part)");

      // Step 2: Shift the high part left by 16 bits to make room for the low part.
      // This multiplies the high part by 2^16, effectively placing it in the
      // upper 16 bits of the 32-bit register.
      emitter.emitInstruction(
          "SHL", targetRegister, "%D 16", targetRegister, "shift hi part left by 16");

      // Step 3: Add the low part to complete the constant.
      // Optimization: If the low part is zero, we skip this step because adding
      // zero has no effect. This reduces code size and execution time for
      // constants that are multiples of 65536.
      if (lo != 0) {
        emitter.emitInstruction("ADD", targetRegister, "%D " + lo, targetRegister, "add lo part");
      }
    }
  }

  /**
   * Checks if a value fits in a 20-bit signed immediate.
   *
   * @param value the value to check
   * @return true if the value fits in [-524288, 524287]
   */
  public static boolean fitsInImmediate(int value) {
    return value >= IMMEDIATE_MIN && value <= IMMEDIATE_MAX;
  }
}
