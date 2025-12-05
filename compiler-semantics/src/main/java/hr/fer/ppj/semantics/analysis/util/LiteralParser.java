package hr.fer.ppj.semantics.analysis.util;

import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.errors.SemanticErrorReporter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Utility class for parsing and validating literals according to PPJ-C specification.
 * 
 * <p>This class handles:
 * <ul>
 *   <li>Integer literals (decimal, octal, hexadecimal)</li>
 *   <li>Float literals</li>
 *   <li>Character literals</li>
 *   <li>String literals</li>
 *   <li>Array length validation</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LiteralParser {
  
  private final SemanticErrorReporter errorReporter;
  
  /**
   * Creates a new literal parser with the specified error reporter.
   * 
   * @param errorReporter the error reporter for validation failures
   * @throws NullPointerException if errorReporter is null
   */
  public LiteralParser(SemanticErrorReporter errorReporter) {
    this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter must not be null");
  }
  
  /**
   * Parses an integer literal and validates it according to PPJ-C rules.
   * 
   * <p>Supports:
   * <ul>
   *   <li>Decimal: {@code 123}</li>
   *   <li>Octal: {@code 0123}</li>
   *   <li>Hexadecimal: {@code 0x123}</li>
   * </ul>
   * 
   * <p>The value must be non-negative and fit within {@code int} range.
   * 
   * @param literal the literal string to parse
   * @param ctx the parse node context for error reporting
   * @return the parsed integer value
   * @throws SemanticException if the literal is invalid
   */
  public long parseIntegerLiteral(String literal, NonTerminalNode ctx) {
    String text = literal.toLowerCase();
    int radix = 10;
    
    if (text.startsWith("0x")) {
      radix = 16;
      text = text.substring(2);
    } else if (text.startsWith("0") && text.length() > 1) {
      radix = 8;
      text = text.substring(1);
    }
    
    if (text.isEmpty()) {
      text = "0";
    }
    
    long value;
    try {
      value = Long.parseLong(text, radix);
    } catch (NumberFormatException ex) {
      errorReporter.reportError(ctx);
      return 0; // Unreachable, but satisfies compiler
    }
    
    if (value < 0 || value > Integer.MAX_VALUE) {
      errorReporter.reportError(ctx);
    }
    
    return value;
  }
  
  /**
   * Parses and validates an array length literal.
   * 
   * <p>The length must be positive and not exceed {@link SemanticConstants#MAX_ARRAY_LENGTH}.
   * 
   * @param literal the literal string to parse
   * @param ctx the parse node context for error reporting
   * @return the parsed array length
   * @throws SemanticException if the length is invalid
   */
  public int parseArrayLength(String literal, NonTerminalNode ctx) {
    long value = parseIntegerLiteral(literal, ctx);
    if (value <= 0 || value > SemanticConstants.MAX_ARRAY_LENGTH) {
      errorReporter.reportError(ctx);
    }
    return (int) value;
  }
  
  /**
   * Validates a float literal according to PPJ-C specification.
   * 
   * <p>A valid float literal must contain either:
   * <ul>
   *   <li>A decimal point ({@code .})</li>
   *   <li>An exponent notation ({@code e} or {@code E})</li>
   * </ul>
   * 
   * @param literal the float literal string (e.g., "3.14", "2.5e10")
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the float literal is malformed
   */
  public void parseFloatLiteral(String literal, NonTerminalNode ctx) {
    if (!literal.contains(".") && !literal.toLowerCase().contains("e")) {
      errorReporter.reportError(ctx);
      return;
    }
    
    try {
      Double.parseDouble(literal);
    } catch (NumberFormatException ex) {
      errorReporter.reportError(ctx);
    }
  }
  
  /**
   * Validates a character literal according to PPJ-C specification.
   * 
   * <p>A valid character literal must:
   * <ul>
   *   <li>Be enclosed in single quotes ({@code '})</li>
   *   <li>Contain exactly one character, or</li>
   *   <li>Contain exactly one escape sequence (\n, \t, \0, \', \")</li>
   * </ul>
   * 
   * @param literal the character literal string including quotes
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the character literal is malformed
   */
  public void parseCharacterLiteral(String literal, NonTerminalNode ctx) {
    if (literal.length() < 3
        || literal.charAt(0) != '\''
        || literal.charAt(literal.length() - 1) != '\'') {
      errorReporter.reportError(ctx);
      return;
    }
    
    // Simple character literal: 'x'
    if (literal.length() == 3) {
      return;
    }
    
    // Escape sequence: '\x' where x is a valid escape character
    if (literal.length() == 4 && literal.charAt(1) == '\\') {
      char escape = literal.charAt(2);
      if (SemanticConstants.VALID_ESCAPE_SEQUENCES.indexOf(escape) >= 0) {
        return;
      }
    }
    
    // Invalid character literal
    errorReporter.reportError(ctx);
  }
  
  /**
   * Computes the length of a string literal for array type checking.
   * 
   * <p>This method implements the semantic rules for string literals. The computed
   * length includes:
   * <ul>
   *   <li>All regular characters in the string</li>
   *   <li>Escape sequences (\n, \t, \0, \', \") count as single characters</li>
   *   <li>The implicit null terminator character</li>
   * </ul>
   * 
   * @param literal the string literal including quotes
   * @param ctx the parse node context for error reporting
   * @return the computed length including null terminator
   * @throws SemanticException if the string literal is malformed
   */
  public int computeStringLiteralLength(String literal, NonTerminalNode ctx) {
    if (literal.length() < 2
        || literal.charAt(0) != '"'
        || literal.charAt(literal.length() - 1) != '"') {
      errorReporter.reportError(ctx);
    }
    
    int length = 0;
    // Process characters between quotes
    for (int i = 1; i < literal.length() - 1; i++) {
      char ch = literal.charAt(i);
      if (ch == '\\') {
        // Handle escape sequence
        if (i + 1 >= literal.length() - 1) {
          errorReporter.reportError(ctx); // Incomplete escape sequence
        }
        char escape = literal.charAt(++i);
        if (SemanticConstants.VALID_ESCAPE_SEQUENCES.indexOf(escape) < 0) {
          errorReporter.reportError(ctx); // Invalid escape sequence
        }
        length++; // Escape sequence counts as one character
      } else {
        if (ch == '"') {
          errorReporter.reportError(ctx); // Unescaped quote inside string
        }
        length++; // Regular character
      }
    }
    return length + 1; // Include null terminator
  }
}

