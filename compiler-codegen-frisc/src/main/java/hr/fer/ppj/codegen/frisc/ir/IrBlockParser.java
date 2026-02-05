package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses block sequences within a function.
 */
final class IrBlockParser {
  private final IrInstructionParser instructionParser;

  IrBlockParser(IrInstructionParser instructionParser) {
    this.instructionParser = instructionParser;
  }

  List<IrProgramModel.Block> parseBlocks(IrLineCursor cursor) {
    List<IrProgramModel.Block> blocks = new ArrayList<>();
    while (true) {
      String peek = cursor.peekNonEmptyLine();
      if (peek == null || peek.trim().equals(".endfunc")) {
        break;
      }
      String labelLine = cursor.nextNonEmptyLine();
      if (labelLine == null) {
        break;
      }
      String trimmed = labelLine.trim();
      if (!trimmed.endsWith(":")) {
        throw new CodeGenerationException("Expected block label, got: " + labelLine);
      }
      String label = trimmed.substring(0, trimmed.length() - 1).trim();

      List<IrProgramModel.Instruction> instructions = new ArrayList<>();
      IrProgramModel.Terminator terminator;

      while (true) {
        String line = cursor.nextNonEmptyLine();
        if (line == null) {
          throw new CodeGenerationException("Unexpected end while parsing block " + label);
        }
        String t = line.trim();
        if (isTerminatorLine(t)) {
          terminator = instructionParser.parseTerminator(t);
          break;
        }
        if (t.endsWith(":")) {
          throw new CodeGenerationException("Missing terminator before block label: " + t);
        }
        instructions.add(instructionParser.parseInstruction(t));
      }

      blocks.add(new IrProgramModel.Block(label, instructions, terminator));
    }
    return blocks;
  }

  private boolean isTerminatorLine(String line) {
    return line.startsWith("br ") || line.startsWith("jmp ") || line.equals("ret") || line.startsWith("ret ");
  }
}
