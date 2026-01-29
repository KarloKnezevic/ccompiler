package hr.fer.ppj.ir.util;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.Objects;

/**
 * Extracts variable and function names from expression nodes.
 *
 * <p>This utility provides methods to extract identifiers from various
 * expression forms, used for address reuse optimizations and function call
 * generation.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ExpressionNameExtractor {

  private ExpressionNameExtractor() {}

  /**
   * Extracts variable name from a simple variable expression node.
   *
   * @param node the expression node
   * @return the variable name, or null if not a simple variable
   */
  public static String extractVariableName(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    String symbol = node.symbol();
    if (symbol.equals("<primarni_izraz>")) {
      return extractVariableNameFromPrimary(node);
    } else if (symbol.equals("<unarni_izraz>") || symbol.equals("<cast_izraz>")
        || symbol.equals("<postfiks_izraz>")) {
      // Check if it's a pass-through to primary expression
      var children = node.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return extractVariableName(child);
      }
    }
    return null;
  }

  /**
   * Extracts the first variable name from an expression (for address reuse in assignments).
   *
   * @param node the expression node
   * @return the first variable name, or null if not found
   */
  public static String extractFirstVariableName(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    String symbol = node.symbol();
    
    // Base case: primary expression with variable
    if (symbol.equals("<primarni_izraz>")) {
      return extractVariableName(node);
    }
    
    // Recursive case: traverse expression tree
    var children = node.children();
    
    // For binary expressions (3+ children: left OP right), check left operand first
    if (children.size() >= 3) {
      ParseNode leftChild = children.get(0);
      if (leftChild instanceof NonTerminalNode left) {
        String leftVar = extractFirstVariableName(left);
        if (leftVar != null) {
          return leftVar;
        }
      }
    }
    
    // For unary expressions or single-child expressions, check the child
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      return extractFirstVariableName(child);
    }
    
    // For assignment expressions, check right side
    if (symbol.equals("<izraz_pridruzivanja>") && children.size() >= 3) {
      ParseNode rightSide = children.get(2);
      if (rightSide instanceof NonTerminalNode right) {
        return extractFirstVariableName(right);
      }
    }
    
    return null;
  }

  /**
   * Extracts function name from a postfix expression that represents a function.
   *
   * @param node the postfix expression node
   * @return the function name, or null if not a function call
   */
  public static String extractFunctionName(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    var children = node.children();
    if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode first) {
      if (first.symbol().equals("<primarni_izraz>")) {
        var primaryChildren = first.children();
        if (!primaryChildren.isEmpty() && primaryChildren.get(0) instanceof TerminalNode term
            && term.symbol().equals("IDN")) {
          return term.lexeme();
        }
      } else if (first.symbol().equals("<postfiks_izraz>")) {
        // Recursively check nested postfix
        return extractFunctionName(first);
      }
    }
    return null;
  }

  /**
   * Extracts array base variable name from an expression (for address reuse).
   *
   * @param node the expression node
   * @return the array base variable name, or null if not found
   */
  public static String extractArrayBaseVarName(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    // For simple variable access, extract the variable name
    String varName = extractVariableName(node);
    if (varName != null) {
      return varName;
    }
    
    // For postfix expressions like a[i], extract from base
    if (node.symbol().equals("<postfiks_izraz>")) {
      var children = node.children();
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode firstChild) {
        return extractArrayBaseVarName(firstChild);
      }
    }
    
    return null;
  }

  private static String extractVariableNameFromPrimary(NonTerminalNode node) {
    var children = node.children();
    if (!children.isEmpty() && children.get(0) instanceof TerminalNode term
        && term.symbol().equals("IDN")) {
      return term.lexeme();
    }
    return null;
  }
}
