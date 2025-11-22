package hr.fer.ppj.lexer.io;

/**
 * Represents a token produced by the lexer.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record Token(
    String type,
    String value,
    int line,
    int column,
    int symbolTableIndex
) {
  
  public static Token of(String type, String value, int line, int column) {
    return new Token(type, value, line, column, -1);
  }
  
  public static Token withIndex(String type, String value, int line, int column, int index) {
    return new Token(type, value, line, column, index);
  }
}

