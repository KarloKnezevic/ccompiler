package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses IR constants.
 */
final class IrConstParser {
  private IrConstParser() {
  }

  static IrConst parse(String text) {
    String trimmed = text.trim();
    if (trimmed.startsWith("null:")) {
      String typeStr = trimmed.substring("null:".length()).trim();
      IrType type = IrTypeParser.parse(typeStr);
      if (!(type instanceof IrPointerType)) {
        throw new CodeGenerationException("Null constant must have pointer type: " + text);
      }
      return new IrConst.NullConst(type);
    }
    if (trimmed.startsWith("#'")) {
      return parseCharConst(trimmed);
    }
    if (trimmed.startsWith("#")) {
      return parseNumericConst(trimmed);
    }
    if (trimmed.startsWith("{")) {
      return parseArrayConst(trimmed);
    }
    throw new CodeGenerationException("Unknown constant: " + text);
  }

  private static IrConst parseNumericConst(String text) {
    int colon = text.lastIndexOf(':');
    if (colon < 0) {
      throw new CodeGenerationException("Invalid numeric constant: " + text);
    }
    String valueStr = text.substring(1, colon).trim();
    String typeStr = text.substring(colon + 1).trim();
    IrType type = IrTypeParser.parse(typeStr);

    if (type == IrPrimitiveType.FLOAT) {
      try {
        float value = Float.parseFloat(valueStr);
        return new IrConst.FloatConst(value);
      } catch (NumberFormatException e) {
        throw new CodeGenerationException("Invalid float constant: " + text);
      }
    }

    int value = IrParseUtil.parseInt(valueStr, "numeric constant");
    return new IrConst.IntConst(value, type);
  }

  private static IrConst parseCharConst(String text) {
    int endQuote = findCharLiteralEnd(text);
    if (endQuote < 0) {
      throw new CodeGenerationException("Invalid char constant: " + text);
    }
    String literal = text.substring(2, endQuote);
    char value = parseCharLiteral(literal);
    return new IrConst.CharConst(value);
  }

  private static int findCharLiteralEnd(String text) {
    boolean escape = false;
    for (int i = 2; i < text.length(); i++) {
      char c = text.charAt(i);
      if (escape) {
        escape = false;
        continue;
      }
      if (c == '\\') {
        escape = true;
        continue;
      }
      if (c == '\'') {
        return i;
      }
    }
    return -1;
  }

  private static char parseCharLiteral(String literal) {
    if (literal.isEmpty()) {
      throw new CodeGenerationException("Empty char literal");
    }
    if (literal.charAt(0) != '\\') {
      return literal.charAt(0);
    }
    if (literal.length() < 2) {
      throw new CodeGenerationException("Invalid escape in char literal");
    }
    return switch (literal.charAt(1)) {
      case 'n' -> '\n';
      case 't' -> '\t';
      case '\\' -> '\\';
      case '\'' -> '\'';
      default -> throw new CodeGenerationException("Unsupported escape in char literal: \\" + literal);
    };
  }

  private static IrConst parseArrayConst(String text) {
    int braceEnd = findMatchingBrace(text);
    if (braceEnd < 0) {
      throw new CodeGenerationException("Invalid array constant: " + text);
    }
    String inside = text.substring(1, braceEnd).trim();
    String after = text.substring(braceEnd + 1).trim();
    int colonIndex = after.indexOf(':');
    if (colonIndex < 0) {
      throw new CodeGenerationException("Array constant missing type: " + text);
    }
    String typeStr = after.substring(colonIndex + 1).trim();
    IrType type = IrTypeParser.parse(typeStr);
    if (!(type instanceof IrArrayType arrayType)) {
      throw new CodeGenerationException("Array constant must have array type: " + text);
    }

    List<IrConst> elements = new ArrayList<>();
    if (!inside.isEmpty()) {
      List<String> parts = IrParseUtil.splitTopLevel(inside, ',');
      for (String part : parts) {
        elements.add(parse(part));
      }
    }

    return new IrConst.ArrayConst(elements, arrayType);
  }

  private static int findMatchingBrace(String text) {
    int depth = 0;
    boolean inChar = false;
    boolean escape = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inChar) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '\'') {
          inChar = false;
        }
        continue;
      }
      if (c == '\'') {
        inChar = true;
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }
}
