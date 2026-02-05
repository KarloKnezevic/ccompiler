package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses struct type definitions.
 */
final class IrStructParser {

  IrProgramModel.StructDef parseStructDef(IrLineCursor cursor) {
    String header = cursor.nextNonEmptyLine();
    String trimmed = header.trim();
    if (!trimmed.startsWith(".type struct")) {
      throw new CodeGenerationException("Expected .type struct, got: " + header);
    }

    String after = trimmed.substring(".type struct".length()).trim();
    int braceIndex = after.indexOf('{');
    if (braceIndex < 0) {
      throw new CodeGenerationException("Struct definition missing '{': " + header);
    }
    String structName = after.substring(0, braceIndex).trim();

    List<IrProgramModel.StructField> fields = new ArrayList<>();
    while (true) {
      String line = cursor.nextNonEmptyLine();
      if (line == null) {
        throw new CodeGenerationException("Unexpected end while parsing struct " + structName);
      }
      String t = line.trim();
      if (t.equals("}")) {
        break;
      }
      int colon = t.indexOf(':');
      int at = t.lastIndexOf('@');
      if (colon < 0 || at < 0 || at < colon) {
        throw new CodeGenerationException("Invalid struct field line: " + line);
      }
      String fieldName = t.substring(0, colon).trim();
      String typeStr = t.substring(colon + 1, at).trim();
      String offsetStr = t.substring(at + 1).trim();
      int offset = IrParseUtil.parseInt(offsetStr, "field offset");
      IrType type = IrTypeParser.parse(typeStr);
      fields.add(new IrProgramModel.StructField(fieldName, type, offset));
    }

    return new IrProgramModel.StructDef(structName, fields);
  }
}
