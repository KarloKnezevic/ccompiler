package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;

/**
 * Utility class for extracting constant values from parse tree nodes.
 *
 * <p>This class provides methods to recursively search parse tree nodes for constant literals
 * (integer numbers and character literals), handling both positive and negative literals (unary
 * minus).
 *
 * <p><b>Supported Literals:</b>
 *
 * <ul>
 *   <li>Integer constants (BROJ terminals)
 *   <li>Character constants (ZNAK terminals) - converted to ASCII values
 *   <li>Negative literals (unary minus applied to constants)
 *   <li>Escape sequences in character literals (\n, \t, \0, \\, \')
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ConstantValueExtractor {

  private ConstantValueExtractor() {
    // Utility class - prevent instantiation
  }

  /**
   * Recursively finds constant value in expression tree. Handles both positive and negative
   * literals.
   *
   * @param node the parse tree node to search
   * @param isChar whether to extract character literals (ZNAK) in addition to integers
   * @return the constant value as string, or null if not found
   */
  public static String findConstantValue(NonTerminalNode node, boolean isChar) {
    return findConstantValueWithSign(node, isChar, false);
  }

  /**
   * Recursively finds constant value in expression tree, tracking sign.
   *
   * <p>This method navigates through expression wrappers (unary expressions, cast expressions,
   * etc.) to find the underlying constant literal, tracking whether a unary minus was applied.
   *
   * @param node the parse tree node to search
   * @param isChar whether to extract character literals (ZNAK) in addition to integers
   * @param isNegative whether a unary minus has been encountered
   * @return the constant value as string (with minus prefix if negative), or null if not found
   */
  private static String findConstantValueWithSign(
      NonTerminalNode node, boolean isChar, boolean isNegative) {
    String symbol = node.symbol();
    var children = node.children();

    // Check for unary minus at <unarni_izraz> level
    if ("<unarni_izraz>".equals(symbol) && children.size() == 2) {
      ParseNode first = children.get(0);
      if (first instanceof NonTerminalNode unaryOp
          && "<unarni_operator>".equals(unaryOp.symbol())) {
        for (ParseNode opChild : unaryOp.children()) {
          if (opChild instanceof TerminalNode terminal && "MINUS".equals(terminal.symbol())) {
            if (children.get(1) instanceof NonTerminalNode operand) {
              return findConstantValueWithSign(operand, isChar, true);
            }
            return null;
          }
        }
      }
    }

    // Check for <primarni_izraz> with BROJ terminal
    if ("<primarni_izraz>".equals(symbol)) {
      for (ParseNode child : children) {
        if (child instanceof TerminalNode terminal) {
          if ("BROJ".equals(terminal.symbol())) {
            String value = terminal.lexeme();
            return isNegative ? "-" + value : value;
          } else if (isChar && "ZNAK".equals(terminal.symbol())) {
            String lexeme = terminal.lexeme();
            if (lexeme.length() >= 3 && lexeme.startsWith("'") && lexeme.endsWith("'")) {
              char ch = lexeme.charAt(1);
              if (ch == '\\' && lexeme.length() >= 4) {
                char next = lexeme.charAt(2);
                switch (next) {
                  case 'n':
                    return "10";
                  case 't':
                    return "9";
                  case '0':
                    return "0";
                  case '\\':
                    return "92";
                  case '\'':
                    return "39";
                  default:
                    return String.valueOf((int) next);
                }
              }
              return String.valueOf((int) ch);
            }
          }
        }
      }
    }

    // Recursively search children
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal) {
        String result = findConstantValueWithSign(nonTerminal, isChar, isNegative);
        if (result != null) return result;
      }
    }

    return null;
  }
}
