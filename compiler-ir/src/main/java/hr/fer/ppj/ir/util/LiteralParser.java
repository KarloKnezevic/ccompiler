package hr.fer.ppj.ir.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses source literals used during IR lowering.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>Character literals with escape sequences: {@code \n}, {@code \t}, {@code \\}, {@code \'}</li>
 *   <li>Integer literals in decimal, octal ({@code 0...}), and hexadecimal ({@code 0x...}) form</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LiteralParser {

  private LiteralParser() {}

  /**
   * Parses a character literal.
   *
   * @param lexeme the character literal lexeme (e.g., 'a', '\n')
   * @return the parsed character value
   */
  public static char parseCharLiteral(String lexeme) {
    Objects.requireNonNull(lexeme, "lexeme must not be null");

    if (lexeme.length() >= 3 && lexeme.startsWith("'") && lexeme.endsWith("'")) {
      String content = lexeme.substring(1, lexeme.length() - 1);
      if (content.equals("\\n")) {
        return '\n';
      } else if (content.equals("\\t")) {
        return '\t';
      } else if (content.equals("\\\\")) {
        return '\\';
      } else if (content.equals("\\'")) {
        return '\'';
      } else if (content.length() == 1) {
        return content.charAt(0);
      }
    }
    return '\0';
  }

  /**
   * Parses a string literal and returns its character sequence including
   * the implicit trailing {@code '\0'} terminator.
   *
   * @param lexeme source string literal lexeme including quotes
   * @return parsed characters with trailing null terminator
   * @throws IllegalArgumentException when the literal is malformed
   */
  public static List<Character> parseStringLiteral(String lexeme) {
    Objects.requireNonNull(lexeme, "lexeme must not be null");
    if (lexeme.length() < 2 || !lexeme.startsWith("\"") || !lexeme.endsWith("\"")) {
      throw new IllegalArgumentException("Invalid string literal: " + lexeme);
    }

    String content = lexeme.substring(1, lexeme.length() - 1);
    List<Character> chars = new ArrayList<>();
    for (int i = 0; i < content.length(); i++) {
      char ch = content.charAt(i);
      if (ch != '\\') {
        chars.add(ch);
        continue;
      }
      if (i + 1 >= content.length()) {
        throw new IllegalArgumentException("Invalid escape sequence in string literal: " + lexeme);
      }
      i++;
      char esc = content.charAt(i);
      chars.add(switch (esc) {
        case 'n' -> '\n';
        case 't' -> '\t';
        case '\\' -> '\\';
        case '\'' -> '\'';
        case '"' -> '"';
        default -> throw new IllegalArgumentException(
            "Unsupported escape sequence \\" + esc + " in string literal: " + lexeme);
      });
    }
    chars.add('\0');
    return chars;
  }

  /**
   * Parses an integer literal produced by the C lexer.
   *
   * <p>The supported syntax intentionally mirrors semantic-analysis rules:
   * decimal ({@code 42}), octal ({@code 052}), and hexadecimal ({@code 0x2A}).
   * A leading sign is accepted for internal helper use.
   *
   * @param lexeme integer literal text
   * @return parsed 32-bit value
   * @throws IllegalArgumentException when the literal is malformed or out of int32 range
   */
  public static int parseIntegerLiteral(String lexeme) {
    Objects.requireNonNull(lexeme, "lexeme must not be null");
    String text = lexeme.trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException("Invalid integer literal: " + lexeme);
    }

    int sign = 1;
    if (text.charAt(0) == '+' || text.charAt(0) == '-') {
      if (text.charAt(0) == '-') {
        sign = -1;
      }
      text = text.substring(1);
    }

    if (text.isEmpty()) {
      throw new IllegalArgumentException("Invalid integer literal: " + lexeme);
    }

    int radix = 10;
    if (text.startsWith("0x") || text.startsWith("0X")) {
      radix = 16;
      text = text.substring(2);
    } else if (text.length() > 1 && text.startsWith("0")) {
      radix = 8;
      text = text.substring(1);
    }

    if (text.isEmpty()) {
      text = "0";
    }

    try {
      long value = Long.parseLong(text, radix) * sign;
      if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Integer literal out of range: " + lexeme);
      }
      return (int) value;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid integer literal: " + lexeme, ex);
    }
  }
}
