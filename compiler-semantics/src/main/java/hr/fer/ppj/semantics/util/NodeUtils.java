package hr.fer.ppj.semantics.util;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;

/**
 * Utilities for working with parse tree nodes.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class NodeUtils {

  private NodeUtils() {}

  public static NonTerminalNode asNonTerminal(ParseNode node, String expectedSymbol) {
    if (node instanceof NonTerminalNode nonTerminal && nonTerminal.symbol().equals(expectedSymbol)) {
      return nonTerminal;
    }
    throw new IllegalStateException(
        "Expected non-terminal " + expectedSymbol + " but found " + node.symbol());
  }

  public static NonTerminalNode asNonTerminal(ParseNode node) {
    if (node instanceof NonTerminalNode nonTerminal) {
      return nonTerminal;
    }
    throw new IllegalStateException("Expected non-terminal but found terminal " + node.symbol());
  }

  public static TerminalNode asTerminal(ParseNode node, String expectedSymbol) {
    if (node instanceof TerminalNode terminal && terminal.symbol().equals(expectedSymbol)) {
      return terminal;
    }
    throw new IllegalStateException(
        "Expected terminal "
            + expectedSymbol
            + " but found "
            + node.symbol());
  }
}

