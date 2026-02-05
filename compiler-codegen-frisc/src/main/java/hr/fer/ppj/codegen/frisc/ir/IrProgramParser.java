package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses full IR programs from text.
 */
final class IrProgramParser {
  private final IrStructParser structParser = new IrStructParser();
  private final IrGlobalsParser globalsParser = new IrGlobalsParser();
  private final IrFunctionParser functionParser;

  IrProgramParser() {
    IrValueParser valueParser = new IrValueParser();
    IrRhsParser rhsParser = new IrRhsParser(valueParser);
    IrInstructionParser instructionParser = new IrInstructionParser(rhsParser, valueParser);
    IrBlockParser blockParser = new IrBlockParser(instructionParser);
    this.functionParser = new IrFunctionParser(blockParser);
  }

  IrProgramModel parse(String text) {
    Objects.requireNonNull(text, "text must not be null");
    IrLineCursor cursor = new IrLineCursor(text);

    String line = cursor.nextNonEmptyLine();
    if (line == null || !line.equals(".program")) {
      throw new CodeGenerationException("IR must start with .program");
    }

    List<IrProgramModel.StructDef> structDefs = new ArrayList<>();
    List<IrProgramModel.GlobalVar> globals = new ArrayList<>();
    List<IrProgramModel.Function> functions = new ArrayList<>();

    while (true) {
      String peek = cursor.peekNonEmptyLine();
      if (peek == null) {
        throw new CodeGenerationException("Unexpected end of IR (missing .endprogram)");
      }
      if (peek.equals(".endprogram")) {
        cursor.nextNonEmptyLine();
        break;
      }
      if (peek.startsWith(".type struct")) {
        structDefs.add(structParser.parseStructDef(cursor));
        continue;
      }
      if (peek.equals(".globals")) {
        cursor.nextNonEmptyLine();
        globals.addAll(globalsParser.parseGlobals(cursor));
        continue;
      }
      if (peek.startsWith(".func")) {
        functions.add(functionParser.parseFunction(cursor));
        continue;
      }
      throw new CodeGenerationException("Unexpected top-level IR line: " + peek);
    }

    return new IrProgramModel(structDefs, globals, functions);
  }
}
