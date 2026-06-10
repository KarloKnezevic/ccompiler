package hr.fer.ppj.cli.vm;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import java.util.List;
import java.util.Map;

/**
 * The fully-lowered bytecode program: a typeless, byte-addressable form of the typed IR.
 *
 * <p>By the time the IR reaches this representation every type, every nested expression, and every
 * symbolic block reference has been compiled away. What remains is a flat byte stream per function,
 * numeric jump offsets, integer slot sizes, and a pre-computed byte image for each global. The
 * virtual machine that runs this needs no knowledge of the IR type system at all.
 */
public final class Bytecode {

  private Bytecode() {
  }

  /**
   * One lowered function: its flat instruction stream plus the metadata the VM needs to enter and
   * leave a call.
   *
   * @param name function name
   * @param code the encoded instruction stream (opcode bytes interleaved with 4-byte operands)
   * @param maxTemps size of the per-frame register file (highest temporary index plus one)
   * @param arity number of parameters
   * @param slots every slot (parameter and local) the call must reserve, in declaration order
   * @param params the parameter slots, in argument order, with how each is bound
   * @param symbols the symbols referenced by {@code ADDR_SYM}, indexed by operand
   * @param blockLabels map from byte offset to the source block label (for disassembly only)
   */
  public record Function(
      String name,
      byte[] code,
      int maxTemps,
      int arity,
      List<SlotInfo> slots,
      List<ParamBind> params,
      List<IrProgramModel.SymbolRef> symbols,
      Map<Integer, String> blockLabels) {
  }

  /** A reserved storage slot for a parameter or local: the VM only needs its size and alignment. */
  public record SlotInfo(IrProgramModel.SlotKind kind, String name, int size, int align) {
  }

  /** How a parameter's incoming argument is written into its slot at call entry. */
  public enum BindKind {
    /** Scalar word (four bytes). */
    WORD,
    /** Scalar byte (char / uchar). */
    BYTE,
    /** Aggregate passed by reference: the argument is a source address; copy {@code size} bytes. */
    AGGREGATE
  }

  /** Binds one argument into its parameter slot. */
  public record ParamBind(String slotName, BindKind bind, int size) {
  }

  /** A global variable's reserved space and pre-computed initial byte image. */
  public record GlobalImage(String name, int size, int align, byte[] image) {
  }

  /** The whole lowered program. */
  public record Program(
      List<Function> functions,
      Map<String, Integer> functionIndex,
      List<GlobalImage> globals) {

    public Function function(int index) {
      return functions.get(index);
    }
  }
}
