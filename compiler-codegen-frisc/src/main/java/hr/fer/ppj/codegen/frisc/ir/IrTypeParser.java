package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;

/**
 * Parses IR type strings.
 */
final class IrTypeParser {
  private final String text;
  private int index;

  private IrTypeParser(String text) {
    this.text = text;
    this.index = 0;
  }

  static IrType parse(String text) {
    IrTypeParser parser = new IrTypeParser(text);
    IrType type = parser.parseType();
    parser.skipWhitespace();
    if (!parser.isAtEnd()) {
      throw new CodeGenerationException("Unexpected trailing type text: " + text);
    }
    return type;
  }

  private IrType parseType() {
    skipWhitespace();
    if (consumeWord("void")) {
      return null;
    }
    if (consumeWord("int32")) {
      return IrPrimitiveType.INT32;
    }
    if (consumeWord("char")) {
      return IrPrimitiveType.CHAR;
    }
    if (consumeWord("uchar")) {
      return IrPrimitiveType.UCHAR;
    }
    if (consumeWord("float")) {
      return IrPrimitiveType.FLOAT;
    }
    if (consumeWord("bool")) {
      return IrPrimitiveType.BOOL;
    }
    if (consumeWord("ptr")) {
      expect('<');
      IrType inner = parseType();
      expect('>');
      return new IrPointerType(inner);
    }
    if (consumeWord("array")) {
      expect('<');
      IrType inner = parseType();
      skipWhitespace();
      expect(',');
      skipWhitespace();
      int size = parseIntToken();
      skipWhitespace();
      expect('>');
      return new IrArrayType(inner, size);
    }
    if (consumeWord("struct")) {
      skipWhitespace();
      String name = parseIdentifier();
      return new IrStructType(name);
    }
    throw new CodeGenerationException("Unknown type: " + text);
  }

  private boolean consumeWord(String word) {
    skipWhitespace();
    if (text.regionMatches(index, word, 0, word.length())) {
      int end = index + word.length();
      if (end == text.length() || !Character.isLetterOrDigit(text.charAt(end))) {
        index = end;
        return true;
      }
    }
    return false;
  }

  private void expect(char c) {
    skipWhitespace();
    if (isAtEnd() || text.charAt(index) != c) {
      throw new CodeGenerationException("Expected '" + c + "' in type: " + text);
    }
    index++;
  }

  private String parseIdentifier() {
    skipWhitespace();
    int start = index;
    while (!isAtEnd()) {
      char c = text.charAt(index);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
        index++;
      } else {
        break;
      }
    }
    if (start == index) {
      throw new CodeGenerationException("Expected identifier in type: " + text);
    }
    return text.substring(start, index);
  }

  private int parseIntToken() {
    skipWhitespace();
    int start = index;
    if (!isAtEnd() && text.charAt(index) == '-') {
      index++;
    }
    while (!isAtEnd() && Character.isDigit(text.charAt(index))) {
      index++;
    }
    if (start == index) {
      throw new CodeGenerationException("Expected integer in type: " + text);
    }
    return IrParseUtil.parseInt(text.substring(start, index), "type integer");
  }

  private void skipWhitespace() {
    while (!isAtEnd() && Character.isWhitespace(text.charAt(index))) {
      index++;
    }
  }

  private boolean isAtEnd() {
    return index >= text.length();
  }
}
