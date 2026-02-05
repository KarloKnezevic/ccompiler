package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses global variable declarations.
 */
final class IrGlobalsParser {

  List<IrProgramModel.GlobalVar> parseGlobals(IrLineCursor cursor) {
    List<IrProgramModel.GlobalVar> globals = new ArrayList<>();
    while (true) {
      String peek = cursor.peekNonEmptyLine();
      if (peek == null || peek.startsWith(".")) {
        break;
      }
      String line = cursor.nextNonEmptyLine().trim();
      if (!line.startsWith("global")) {
        throw new CodeGenerationException("Invalid global line: " + line);
      }
      String rest = line.substring("global".length()).trim();
      int colon = rest.indexOf(':');
      if (colon < 0) {
        throw new CodeGenerationException("Invalid global line: " + line);
      }
      String name = rest.substring(0, colon).trim();
      String afterColon = rest.substring(colon + 1).trim();

      String typeStr = afterColon;
      String initStr = null;
      int eqIndex = IrParseUtil.indexOfTopLevel(afterColon, '=');
      if (eqIndex >= 0) {
        typeStr = afterColon.substring(0, eqIndex).trim();
        initStr = afterColon.substring(eqIndex + 1).trim();
      }

      IrType type = IrTypeParser.parse(typeStr);
      IrConst initializer = null;
      if (initStr != null && !initStr.isEmpty()) {
        initializer = IrConstParser.parse(initStr);
      }

      globals.add(new IrProgramModel.GlobalVar(name, type, initializer));
    }
    return globals;
  }
}
