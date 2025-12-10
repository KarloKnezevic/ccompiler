package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.util.FloatCodegenHelper;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.Objects;

/**
 * Extracts literal values from parse tree nodes.
 *
 * <p>This utility class provides methods to extract integer and float literal values from
 * expression nodes, which is useful for constant folding optimizations.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LiteralExtractor {

  /**
   * Tries to extract an integer literal value from an expression.
   *
   * <p>This is used for optimization - if the operand is a constant, we can directly emit the
   * negated value instead of computing it at runtime.
   *
   * @param expr the expression node
   * @return the literal value as a string, or null if not a literal
   */
  public static String tryExtractIntegerLiteral(NonTerminalNode expr) {
    Objects.requireNonNull(expr, "expr must not be null");

    // Recursively search for a BROJ terminal
    for (ParseNode child : expr.children()) {
      if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
        String lexeme = terminal.lexeme();
        // Only return if it's NOT a float literal (no '.' or 'e'/'E')
        if (!FloatCodegenHelper.isFloatLiteral(lexeme)) {
          return lexeme;
        }
      } else if (child instanceof NonTerminalNode nonTerminal) {
        String result = tryExtractIntegerLiteral(nonTerminal);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  /**
   * Tries to extract a float literal value from an expression.
   *
   * <p>This is used for optimization - if the operand is a float constant, we can directly emit the
   * negated Q16.16 value instead of computing it at runtime.
   *
   * @param expr the expression node
   * @return the float literal value as a string, or null if not a float literal
   */
  public static String tryExtractFloatLiteral(NonTerminalNode expr) {
    Objects.requireNonNull(expr, "expr must not be null");

    // Recursively search for a BROJ terminal that is a float literal
    for (ParseNode child : expr.children()) {
      if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
        String lexeme = terminal.lexeme();
        // Only return if it IS a float literal (contains '.' or 'e'/'E')
        if (FloatCodegenHelper.isFloatLiteral(lexeme)) {
          return lexeme;
        }
      } else if (child instanceof NonTerminalNode nonTerminal) {
        String result = tryExtractFloatLiteral(nonTerminal);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }
}
