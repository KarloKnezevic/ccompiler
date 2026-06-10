package hr.fer.ppj.cli.vm;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a {@link Bytecode.Program} as human-readable assembly-style text.
 *
 * <p>The output is the bytecode analogue of an {@code .ir} or {@code .frisc} dump: one line per
 * instruction, prefixed with its byte offset, with block labels restored from the lowerer's
 * side-table and symbolic operands (temporaries, symbols, call targets, branch labels) printed in
 * place of their raw numeric encoding.
 */
public final class BytecodeDisassembler {

  public String disassemble(Bytecode.Program program) {
    Map<Integer, String> functionNames = new HashMap<>();
    for (Map.Entry<String, Integer> entry : program.functionIndex().entrySet()) {
      functionNames.put(entry.getValue(), entry.getKey());
    }

    StringBuilder out = new StringBuilder(4096);
    out.append("; FRISCcc bytecode disassembly\n\n");
    for (int f = 0; f < program.functions().size(); f++) {
      disassembleFunction(out, program.functions().get(f), functionNames);
      out.append('\n');
    }
    return out.toString();
  }

  private void disassembleFunction(StringBuilder out, Bytecode.Function fn, Map<Integer, String> functionNames) {
    out.append(".func ").append(fn.name())
        .append("  (params=").append(fn.arity())
        .append(", temps=").append(fn.maxTemps())
        .append(", code=").append(fn.code().length).append(" bytes)\n");

    byte[] code = fn.code();
    int pc = 0;
    while (pc < code.length) {
      String label = fn.blockLabels().get(pc);
      if (label != null) {
        out.append(label).append(":\n");
      }
      Opcode op = Opcode.fromByte(code[pc]);
      out.append(String.format("  %4d: %-14s", pc, op.name()));
      int operandPc = pc + 1;
      appendOperands(out, fn, op, operandPc, functionNames);
      out.append('\n');
      pc += op.encodedSize();
    }
  }

  private void appendOperands(StringBuilder out, Bytecode.Function fn, Opcode op, int operandPc,
      Map<Integer, String> functionNames) {
    byte[] code = fn.code();
    switch (op) {
      case PUSH_CONST -> out.append('#').append(readInt(code, operandPc));
      case LOAD_TEMP, STORE_TEMP -> out.append('t').append(readInt(code, operandPc));
      case ADDR_SYM -> {
        IrProgramModel.SymbolRef ref = fn.symbols().get(readInt(code, operandPc));
        out.append(ref.kind().name().toLowerCase()).append(':').append(ref.name());
      }
      case ADDR_FIELD -> out.append("+").append(readInt(code, operandPc));
      case ADDR_INDEX -> out.append("elem=").append(readInt(code, operandPc));
      case ADDR_INDEX_CHK -> out.append("elem=").append(readInt(code, operandPc))
          .append(" size=").append(readInt(code, operandPc + 4));
      case MEMCPY -> out.append(readInt(code, operandPc)).append(" bytes");
      case CALL, CALL_VOID -> {
        int funcIndex = readInt(code, operandPc);
        int argc = readInt(code, operandPc + 4);
        out.append(functionNames.getOrDefault(funcIndex, "?" + funcIndex))
            .append('/').append(argc);
      }
      case JMP -> appendTarget(out, fn, readInt(code, operandPc));
      case BR -> {
        appendTarget(out, fn, readInt(code, operandPc));
        out.append(", ");
        appendTarget(out, fn, readInt(code, operandPc + 4));
      }
      default -> { /* no operands */ }
    }
  }

  private void appendTarget(StringBuilder out, Bytecode.Function fn, int offset) {
    String label = fn.blockLabels().get(offset);
    if (label != null) {
      out.append(label);
    } else {
      out.append('@').append(offset);
    }
  }

  private static int readInt(byte[] code, int pc) {
    return (code[pc] & 0xFF)
        | ((code[pc + 1] & 0xFF) << 8)
        | ((code[pc + 2] & 0xFF) << 16)
        | ((code[pc + 3] & 0xFF) << 24);
  }
}
