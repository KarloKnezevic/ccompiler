package hr.fer.ppj.ir.util;

import java.util.Objects;

/**
 * Parses character literals.
 *
 * <p>Handles escape sequences: \n, \t, \\, \'
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
}
