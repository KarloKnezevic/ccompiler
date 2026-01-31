package hr.fer.ppj.ir.util;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.Objects;

/**
 * Checks if an expression node is addressable (can be used as an l-value).
 *
 * <p>This utility determines addressability by examining the parse tree structure,
 * NOT by relying on lvalue flags. An expression is addressable if it is:
 * <ul>
 *   <li>An identifier (variable, parameter, or global)</li>
 *   <li>A dereference expression: {@code *expr}</li>
 *   <li>An array indexing expression: {@code expr[index]}</li>
 *   <li>A struct field access: {@code expr.field}</li>
 * </ul>
 *
 * <p>Function calls are NOT addressable (they're rvalues only).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class AddressabilityChecker {

  private AddressabilityChecker() {}

  /**
   * Checks if an expression node is addressable by examining its form.
   *
   * @param node the expression node
   * @return true if the expression is addressable
   */
  public static boolean isAddressableExpressionForm(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    String symbol = node.symbol();

    // Primary expression: identifier or parenthesized addressable expression
    if (symbol.equals("<primarni_izraz>")) {
      var children = node.children();
      if (!children.isEmpty()) {
        ParseNode first = children.get(0);
        if (first instanceof TerminalNode term) {
          if (term.symbol().equals("IDN")) {
            return true;
          }
          // Parenthesized expression: check if inner expression is addressable
          if (term.symbol().equals("L_ZAGRADA") && children.size() >= 3) {
            ParseNode middle = children.get(1);
            if (middle instanceof NonTerminalNode inner) {
              return isAddressableExpressionForm(inner);
            }
          }
        }
      }
    }

    // Postfix expression: array indexing, field access, parentheses, or postfix inc/dec
    // NOTE: Function calls are NOT addressable (they're rvalues only)
    if (symbol.equals("<postfiks_izraz>")) {
      var children = node.children();
      if (children.size() >= 3) {
        ParseNode first = children.get(0);
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term) {
          // Array indexing: expr[index]
          if (term.symbol().equals("L_UGL_ZAGRADA")) {
            return true;
          }
          // Field access: expr.field
          if (term.symbol().equals("TOCKA")) {
            return true;
          }
          // Function call: <postfiks_izraz> L_ZAGRADA ... D_ZAGRADA - NOT addressable
          if (term.symbol().equals("L_ZAGRADA")) {
            // If first child is a non-terminal (not L_ZAGRADA), it's a function call
            if (first instanceof NonTerminalNode) {
              return false; // Function call - not addressable
            }
          }
        }
      }
      // Postfix increment/decrement: expr++ or expr-- (the expr itself is addressable)
      if (children.size() == 2) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term) {
          if (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC")) {
            if (children.get(0) instanceof NonTerminalNode base) {
              return isAddressableExpressionForm(base);
            }
          }
        }
      }
      // Recursively check base expression (for nested postfix like a[i].field)
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode base) {
        return isAddressableExpressionForm(base);
      }
    }

    // Unary expression: dereference, or pass-through to cast/postfix
    if (symbol.equals("<unarni_izraz>")) {
      var children = node.children();
      if (children.size() >= 2) {
        ParseNode first = children.get(0);
        // Check for dereference: *expr (terminal ASTERISK)
        if (first instanceof TerminalNode term && term.symbol().equals("ASTERISK")) {
          return true; // Dereference: *expr
        }
        // Also check for <unarni_operator> containing ASTERISK
        if (first instanceof NonTerminalNode nt && nt.symbol().equals("<unarni_operator>")) {
          var opChildren = nt.children();
          if (!opChildren.isEmpty() && opChildren.get(0) instanceof TerminalNode opTerm) {
            if (opTerm.symbol().equals("ASTERISK")) {
              return true; // Dereference: *expr
            }
          }
        }
      }
      // If no unary operator, check if the child contains an addressable expression
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return isAddressableExpressionForm(child);
      }
    }

    // Cast expression: pass-through to unary expression if not an explicit cast
    if (symbol.equals("<cast_izraz>")) {
      var children = node.children();
      // If it's just a pass-through to <unarni_izraz>, check the child
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return isAddressableExpressionForm(child);
      }
      // Explicit cast (type)expr is NOT addressable
    }

    // Expression types that pass through to child expressions
    if (symbol.equals("<izraz>") || symbol.equals("<izraz_pridruzivanja>")
        || symbol.equals("<log_ili_izraz>") || symbol.equals("<log_i_izraz>")
        || symbol.equals("<bin_ili_izraz>") || symbol.equals("<bin_xili_izraz>")
        || symbol.equals("<bin_i_izraz>") || symbol.equals("<jednakosni_izraz>")
        || symbol.equals("<odnosni_izraz>") || symbol.equals("<aditivni_izraz>")
        || symbol.equals("<multiplikativni_izraz>")) {
      var children = node.children();
      // If it's a pass-through (single child), check the child
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return isAddressableExpressionForm(child);
      }
    }

    return false;
  }

  /**
   * Recursively finds an addressable sub-expression within a non-addressable expression.
   *
   * <p>Used for array arguments where the expression might be nested (e.g., in parentheses or cast).
   *
   * @param node the expression node
   * @return an addressable sub-expression, or null if not found
   */
  public static NonTerminalNode findAddressableSubExpression(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    String symbol = node.symbol();

    // Check common nesting cases
    if (symbol.equals("<cast_izraz>") || symbol.equals("<unarni_izraz>")) {
      var children = node.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        // Pass-through - check child
        if (isAddressableExpressionForm(child)) {
          return child;
        }
        // Recursively search in child
        return findAddressableSubExpression(child);
      }
    }

    if (symbol.equals("<primarni_izraz>")) {
      var children = node.children();
      if (!children.isEmpty() && children.get(0) instanceof TerminalNode term
          && term.symbol().equals("IDN")) {
        if (isAddressableExpressionForm(node)) {
          return node;
        }
      }
    }

    if (symbol.equals("<postfiks_izraz>")) {
      // Postfix expression (array indexing, etc.)
      if (isAddressableExpressionForm(node)) {
        return node;
      }
      // Check if it's a nested postfix
      var children = node.children();
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode child) {
        return findAddressableSubExpression(child);
      }
    }

    // Check all children recursively
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (isAddressableExpressionForm(nt)) {
          return nt;
        }
        NonTerminalNode found = findAddressableSubExpression(nt);
        if (found != null) {
          return found;
        }
      }
    }

    return null;
  }
}
