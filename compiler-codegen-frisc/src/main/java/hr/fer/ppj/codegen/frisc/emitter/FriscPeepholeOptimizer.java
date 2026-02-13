package hr.fer.ppj.codegen.frisc.emitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs a small, semantics-preserving peephole cleanup on emitted FRISC text.
 */
final class FriscPeepholeOptimizer {

  List<String> optimize(List<String> input) {
    List<String> current = List.copyOf(input);
    boolean changed;
    do {
      Result result = pass(current);
      current = result.lines();
      changed = result.changed();
    } while (changed);
    return current;
  }

  private Result pass(List<String> input) {
    List<String> output = new ArrayList<>(input.size());
    boolean changed = false;

    for (int i = 0; i < input.size(); i++) {
      String line = input.get(i);

      if (isSelfMove(line)) {
        changed = true;
        continue;
      }

      if (isIdentityNoOp(line)) {
        changed = true;
        continue;
      }

      if (i + 1 < input.size() && isPushPopPair(line, input.get(i + 1))) {
        i++;
        changed = true;
        continue;
      }

      if (isJumpToNextLabel(input, i)) {
        changed = true;
        continue;
      }

      output.add(line);
    }

    return new Result(List.copyOf(output), changed);
  }

  private boolean isSelfMove(String line) {
    ParsedInstruction ins = parseInstruction(line);
    return ins != null
        && ins.mnemonic().equals("MOVE")
        && ins.operands().size() == 2
        && ins.operands().get(0).equals(ins.operands().get(1));
  }

  private boolean isPushPopPair(String first, String second) {
    ParsedInstruction push = parseInstruction(first);
    ParsedInstruction pop = parseInstruction(second);
    return push != null
        && pop != null
        && push.mnemonic().equals("PUSH")
        && pop.mnemonic().equals("POP")
        && push.operands().size() == 1
        && pop.operands().size() == 1
        && push.operands().get(0).equals(pop.operands().get(0));
  }

  private boolean isIdentityNoOp(String line) {
    ParsedInstruction ins = parseInstruction(line);
    if (ins == null || ins.operands().size() != 3) {
      return false;
    }

    String left = ins.operands().get(0);
    String middle = ins.operands().get(1);
    String dest = ins.operands().get(2);
    if (!left.equals(dest) || !isZeroImmediate(middle)) {
      return false;
    }

    return ins.mnemonic().equals("ADD")
        || ins.mnemonic().equals("SUB")
        || ins.mnemonic().equals("OR")
        || ins.mnemonic().equals("XOR")
        || ins.mnemonic().equals("SHL")
        || ins.mnemonic().equals("SHR");
  }

  private boolean isZeroImmediate(String operand) {
    return operand.equals("0") || operand.equals("+0") || operand.equals("-0");
  }

  private boolean isJumpToNextLabel(List<String> lines, int jumpIndex) {
    ParsedInstruction jump = parseInstruction(lines.get(jumpIndex));
    if (jump == null || !jump.mnemonic().equals("JP") || jump.operands().size() != 1) {
      return false;
    }

    String target = jump.operands().get(0);
    for (int i = jumpIndex + 1; i < lines.size(); i++) {
      String code = codePart(lines.get(i));
      if (code.isBlank()) {
        continue;
      }
      return code.equals(target);
    }

    return false;
  }

  private ParsedInstruction parseInstruction(String line) {
    String code = codePart(line);
    if (code.isBlank() || !Character.isWhitespace(line.isEmpty() ? ' ' : line.charAt(0))) {
      return null;
    }

    int firstSpace = code.indexOf(' ');
    if (firstSpace < 0) {
      return new ParsedInstruction(code, List.of());
    }

    String mnemonic = code.substring(0, firstSpace).trim();
    String operandPart = code.substring(firstSpace + 1).trim();
    if (operandPart.isEmpty()) {
      return new ParsedInstruction(mnemonic, List.of());
    }

    String[] raw = operandPart.split(",");
    List<String> operands = new ArrayList<>(raw.length);
    for (String operand : raw) {
      operands.add(operand.trim());
    }
    return new ParsedInstruction(mnemonic, List.copyOf(operands));
  }

  private String codePart(String line) {
    int commentStart = line.indexOf(';');
    if (commentStart < 0) {
      return line.trim();
    }
    return line.substring(0, commentStart).trim();
  }

  private record ParsedInstruction(String mnemonic, List<String> operands) {
  }

  private record Result(List<String> lines, boolean changed) {
  }
}
