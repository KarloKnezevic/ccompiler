package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;

/**
 * Analyzes expression nodes for structural properties.
 *
 * <p>Provides predicates for determining expression characteristics such as:
 * <ul>
 *   <li>Whether an expression contains function calls</li>
 *   <li>Whether an expression is a simple variable reference</li>
 *   <li>Whether an expression contains array indexing</li>
 *   <li>Whether an expression contains postfix increment/decrement</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ExpressionAnalyzer {

  private ExpressionAnalyzer() {}

  /**
   * Checks if an expression node contains a function call.
   */
  public static boolean containsFunctionCall(NonTerminalNode node) {
    String symbol = node.symbol();

    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode first = children.get(0);
        ParseNode second = children.get(1);
        if (first instanceof NonTerminalNode && second instanceof TerminalNode term
            && term.symbol().equals("L_ZAGRADA")) {
          return true;
        }
      }
    }

    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt && containsFunctionCall(nt)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Checks if an expression node is a simple variable (just an identifier).
   */
  public static boolean isSimpleVariable(NonTerminalNode node) {
    String symbol = node.symbol();

    if (symbol.equals("<primarni_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 1 && children.get(0) instanceof TerminalNode term
          && term.symbol().equals("IDN")) {
        return true;
      }
    }

    if (isPassThroughSymbol(symbol)) {
      List<ParseNode> children = node.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return isSimpleVariable(child);
      }
    }

    return false;
  }

  /**
   * Checks if an expression node contains array indexing.
   */
  public static boolean containsArrayIndexing(NonTerminalNode node) {
    String symbol = node.symbol();

    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term && term.symbol().equals("L_UGL_ZAGRADA")) {
          return true;
        }
      }
    }

    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt && containsArrayIndexing(nt)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Checks if an expression node contains a postfix increment or decrement.
   */
  public static boolean containsPostfixIncrement(NonTerminalNode node) {
    String symbol = node.symbol();

    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 2) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term) {
          if (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC")) {
            return true;
          }
        }
      }
    }

    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt && containsPostfixIncrement(nt)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Recursively finds the postfix increment node by unwrapping expression wrappers.
   *
   * @return the postfix increment node, or null if not found
   */
  public static NonTerminalNode findPostfixIncrementNode(NonTerminalNode node) {
    String symbol = node.symbol();

    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 2) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term
            && (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC"))) {
          return node;
        }
      }
    }

    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        NonTerminalNode found = findPostfixIncrementNode(nt);
        if (found != null) {
          return found;
        }
      }
    }

    return null;
  }

  private static boolean isPassThroughSymbol(String symbol) {
    return symbol.equals("<unarni_izraz>") || symbol.equals("<cast_izraz>")
        || symbol.equals("<postfiks_izraz>") || symbol.equals("<izraz>")
        || symbol.equals("<izraz_pridruzivanja>") || symbol.equals("<log_ili_izraz>")
        || symbol.equals("<log_i_izraz>") || symbol.equals("<bin_ili_izraz>")
        || symbol.equals("<bin_xili_izraz>") || symbol.equals("<bin_i_izraz>")
        || symbol.equals("<jednakosni_izraz>") || symbol.equals("<odnosni_izraz>")
        || symbol.equals("<aditivni_izraz>") || symbol.equals("<multiplikativni_izraz>");
  }
}
