package hr.fer.ppj.cli.vm;

/**
 * The instruction set of the FRISCcc bytecode virtual machine.
 *
 * <p>Each opcode occupies a single byte in the encoded instruction stream (its {@link #ordinal()}),
 * optionally followed by a fixed number of 4-byte little-endian integer operands as declared by
 * {@link #operandCount()}. The machine is a <em>stack machine</em>: arithmetic, comparison, address,
 * and memory opcodes consume their inputs from an operand stack and push their result back, while the
 * named temporaries of the typed IR survive lowering as a per-frame register file addressed by
 * {@code LOAD_TEMP} / {@code STORE_TEMP}.
 *
 * <p>This enum is deliberately the only place where opcode identity, operand arity, and mnemonic
 * live together, so the lowerer ({@link IrToBytecodeCompiler}), the dispatcher ({@link BytecodeVm}),
 * and the disassembler ({@link BytecodeDisassembler}) cannot drift apart.
 */
public enum Opcode {

  // --- stack and register-file traffic ------------------------------------
  /** Push an immediate 32-bit constant (already decoded to its integer encoding). */
  PUSH_CONST(1),
  /** Push the current value of temporary {@code t<idx>} from the frame's register file. */
  LOAD_TEMP(1),
  /** Pop the top of stack into temporary {@code t<idx>}. */
  STORE_TEMP(1),

  // --- address computation -------------------------------------------------
  /** Push the address of the symbol at {@code symbols[idx]} (local, param, or global). */
  ADDR_SYM(1),
  /** Pop a base address, push {@code base + fieldOffset}. */
  ADDR_FIELD(1),
  /** Pop index, pop base, push {@code base + index * elemSize}. */
  ADDR_INDEX(1),
  /** Bounds-checked variant: operands {@code elemSize, arraySize}; traps with code -6 on overflow. */
  ADDR_INDEX_CHK(2),

  // --- memory --------------------------------------------------------------
  /** Pop an address, push the 32-bit word loaded from it. */
  LOAD_WORD(0),
  /** Pop an address, push the zero-extended byte loaded from it. */
  LOAD_BYTE(0),
  /** Pop a value then an address; store the low word at the address. */
  STORE_WORD(0),
  /** Pop a value then an address; store the low byte at the address. */
  STORE_BYTE(0),
  /** Pop a source address then a destination address; copy {@code size} bytes. */
  MEMCPY(1),

  // --- integer arithmetic and bitwise -------------------------------------
  ADD(0), SUB(0), MUL(0), DIV(0), MOD(0),
  AND(0), OR(0), XOR(0), SHL(0), SHR(0),

  // --- Q16.16 fixed-point (the only ops where float differs from int) ------
  /** Fixed-point multiply: {@code (int)((long)a * b >> 16)}. */
  MUL_Q16(0),
  /** Fixed-point divide: {@code (int)(((long)a << 16) / b)}, zero on divide-by-zero. */
  DIV_Q16(0),

  // --- comparison (push 1 or 0) -------------------------------------------
  CMP_EQ(0), CMP_NE(0), CMP_LT(0), CMP_LE(0), CMP_GT(0), CMP_GE(0),

  // --- unary ---------------------------------------------------------------
  NEG(0), NOT(0),

  // --- casts ---------------------------------------------------------------
  /** Truncate / zero-extend to a byte: {@code value & 0xFF}. */
  CAST_BYTE(0),
  /** Sign-extend a byte: {@code (value << 24) >> 24}. */
  CAST_SEXT(0),
  /** Integer to Q16.16: {@code value << 16}. */
  CAST_ITOF(0),
  /** Q16.16 to integer: {@code value >> 16}. */
  CAST_FTOI(0),

  // --- calls (operands: funcIndex, argCount) ------------------------------
  /** Call, pushing the callee's return value for the caller to consume. */
  CALL(2),
  /** Call for effect; the callee's return value is discarded. */
  CALL_VOID(2),

  // --- control flow (operands are absolute byte offsets within the chunk) --
  JMP(1),
  /** Pop a condition; branch to {@code trueOffset} when non-zero, else {@code falseOffset}. */
  BR(2),
  /** Pop the return value and return it to the caller. */
  RET(0),
  /** Return with no value (yields 0). */
  RET_VOID(0);

  private final int operandCount;

  Opcode(int operandCount) {
    this.operandCount = operandCount;
  }

  /** Number of 4-byte integer operands that follow this opcode in the stream. */
  public int operandCount() {
    return operandCount;
  }

  /** Total encoded size in bytes: one opcode byte plus four bytes per operand. */
  public int encodedSize() {
    return 1 + 4 * operandCount;
  }

  private static final Opcode[] BY_BYTE = values();

  /** Decode an opcode byte back into its enum constant. */
  public static Opcode fromByte(int b) {
    int index = b & 0xFF;
    if (index >= BY_BYTE.length) {
      throw new IllegalStateException("Unknown opcode byte: " + index);
    }
    return BY_BYTE[index];
  }
}
