package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Orchestrates generation of FRISC helper functions for floating-point operations.
 *
 * <p>FRISC architecture has no native hardware support for floating-point operations. This class
 * coordinates the generation of software routines that implement floating-point arithmetic using
 * <b>Q16.16 fixed-point representation</b> by delegating to specialized generators. It acts as a
 * <b>facade</b> that manages the generation of all float helper functions based on which operations
 * are actually needed by the generated code.
 *
 * <p><b>Design Pattern: Facade</b>
 *
 * <p>This class implements the <b>facade pattern</b>, providing a simplified interface for
 * generating multiple related helper functions. It:
 *
 * <ul>
 *   <li>Hides the complexity of managing multiple generators
 *   <li>Provides a single entry point for float helper generation
 *   <li>Only generates helpers that are actually needed (lazy generation)
 *   <li>Manages dependencies between helpers
 * </ul>
 *
 * <p><b>Float Representation: Q16.16 Fixed-Point Format</b>
 *
 * <p>Floats are represented as 32-bit signed integers using <b>Q16.16 fixed-point format</b>:
 *
 * <ul>
 *   <li><b>Bits 31-16:</b> Integer part (signed 16-bit, range -32768 to 32767)
 *   <li><b>Bits 15-0:</b> Fractional part (unsigned 16-bit, represents value/65536)
 *   <li><b>Scaling Factor:</b> 65536 = 2^16
 *   <li><b>Actual Value:</b> stored_integer / 65536.0
 * </ul>
 *
 * <p><b>Q16.16 Format Examples:</b>
 *
 * <pre>
 * 0x00010000 = 1.0      (65536 / 65536 = 1.0)
 * 0x00018000 = 1.5      (98304 / 65536 = 1.5)
 * 0x00020000 = 2.0      (131072 / 65536 = 2.0)
 * 0xFFFE8000 = -1.5     (-98304 / 65536 = -1.5)
 * 0x00008000 = 0.5      (32768 / 65536 = 0.5)
 * </pre>
 *
 * <p><b>Why Fixed-Point Instead of IEEE-754?</b>
 *
 * <p>Fixed-point representation is used because:
 *
 * <ul>
 *   <li><b>Simplicity:</b> Fixed-point arithmetic is simpler to implement in software
 *   <li><b>Performance:</b> Integer operations are faster than floating-point emulation
 *   <li><b>Precision:</b> Q16.16 provides sufficient precision for most applications (16 bits of
 *       fractional precision ≈ 4-5 decimal digits)
 *   <li><b>Range:</b> Supports values from approximately -32768.0 to 32767.99998
 *   <li><b>No Special Cases:</b> No need to handle infinity, NaN, or denormalized numbers
 * </ul>
 *
 * <p><b>Helper Functions Generated:</b>
 *
 * <p>This class delegates to specialized generators:
 *
 * <ul>
 *   <li><b>{@link FloatAddGenerator}:</b> Generates F_FADD for float addition. Simple integer
 *       addition (O(1)).
 *   <li><b>{@link FloatSubGenerator}:</b> Generates F_FSUB for float subtraction. Simple integer
 *       subtraction (O(1)).
 *   <li><b>{@link FloatMulGenerator}:</b> Generates F_FMUL for float multiplication. Uses 64-bit
 *       multiplication and Q16.16 conversion (O(32)).
 *   <li><b>{@link FloatDivGenerator}:</b> Generates F_FDIV for float division. Uses 64-bit division
 *       (O(64)).
 *   <li><b>{@link FloatCmpGenerator}:</b> Generates F_FCMP for float comparison. Simple integer
 *       comparison (O(1)).
 *   <li><b>{@link IntToFloatGenerator}:</b> Generates F_I2F for integer to float conversion. Left
 *       shift by 16 bits (O(1)).
 *   <li><b>{@link FloatToIntGenerator}:</b> Generates F_F2I for float to integer conversion. Right
 *       shift by 16 bits (O(1)).
 * </ul>
 *
 * <p><b>Dependency Management:</b>
 *
 * <p>Some float helpers depend on integer helpers:
 *
 * <ul>
 *   <li>None currently - F_FMUL uses only 32-bit operations internally
 * </ul>
 *
 * <p>This class marks these integer helpers as needed when generating the corresponding float
 * helpers, ensuring they are generated later in the code generation pipeline.
 *
 * <p><b>Lazy Generation Strategy:</b>
 *
 * <p>Helpers are only generated if they are actually needed:
 *
 * <ol>
 *   <li>During expression code generation, float operations call methods like {@code
 *       emitter.markFloatAddNeeded()} to mark which helpers are needed
 *   <li>After processing the translation unit, the main code generator queries which float helpers
 *       are needed
 *   <li>This class generates only the needed helpers, avoiding unnecessary code bloat
 * </ol>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of helpers to generate (typically 0-7
 *       helpers, each O(1) to O(64) depending on the helper)
 *   <li><b>Space Complexity:</b> O(1) - uses only a few generator objects
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatHelperGenerator {

  private final FloatAddGenerator addGenerator = new FloatAddGenerator();
  private final FloatSubGenerator subGenerator = new FloatSubGenerator();
  private final FloatMulGenerator mulGenerator = new FloatMulGenerator();
  private final FloatDivGenerator divGenerator = new FloatDivGenerator();
  private final FloatCmpGenerator cmpGenerator = new FloatCmpGenerator();
  private final IntToFloatGenerator i2fGenerator = new IntToFloatGenerator();
  private final FloatToIntGenerator f2iGenerator = new FloatToIntGenerator();

  /**
   * Generates all float helper functions that are needed.
   *
   * @param context the code generation context
   * @param needsAdd whether F_FADD is needed
   * @param needsSub whether F_FSUB is needed
   * @param needsMul whether F_FMUL is needed
   * @param needsDiv whether F_FDIV is needed
   * @param needsCmp whether F_FCMP is needed
   * @param needsI2F whether F_I2F is needed
   * @param needsF2I whether F_F2I is needed
   */
  public void generateFloatHelpers(
      CodeGenContext context,
      boolean needsAdd,
      boolean needsSub,
      boolean needsMul,
      boolean needsDiv,
      boolean needsCmp,
      boolean needsI2F,
      boolean needsF2I) {
    Objects.requireNonNull(context, "context must not be null");

    FriscEmitter emitter = context.emitter();

    if (!needsAdd && !needsSub && !needsMul && !needsDiv && !needsCmp && !needsI2F && !needsF2I) {
      return; // No float helpers needed
    }

    emitter.emitComment("Float helper functions (IEEE-754 single-precision)");
    emitter.emitNewline();

    if (needsAdd) {
      addGenerator.generate(context);
    }
    if (needsSub) {
      subGenerator.generate(context);
    }
    if (needsMul) {
      // F_FMUL now uses only 32-bit Russian peasant algorithm internally.
      // No dependency on F_MUL64 or F_MUL.
      mulGenerator.generate(context);
    }
    if (needsDiv) {
      divGenerator.generate(context);
    }
    if (needsCmp) {
      cmpGenerator.generate(context);
    }
    if (needsI2F) {
      i2fGenerator.generate(context);
    }
    if (needsF2I) {
      f2iGenerator.generate(context);
    }
  }
}
