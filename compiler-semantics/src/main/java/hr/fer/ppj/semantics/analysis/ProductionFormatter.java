package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.StringJoiner;

/**
 * Utility for formatting productions in the exact textual format required by PPJ graders.
 * Terminals are rendered as {@code TERMINAL(line,lexeme)} whereas non-terminals keep their raw
 * symbols. Keeping the formatting logic centralized avoids subtle drift between error sites.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class ProductionFormatter {

  private ProductionFormatter() {}

  /**
   * Renders the provided non-terminal by walking its children in order and concatenating them into
   * the canonical {@code <production> ::= RHS} shape.
   */
  static String formatProduction(NonTerminalNode node) {
    StringJoiner joiner = new StringJoiner(" ", node.symbol() + " ::= ", "");
    for (ParseNode child : node.children()) {
      joiner.add(formatSymbol(child));
    }
    return joiner.toString();
  }

  /**
   * Formats a single symbol. Terminals receive positional metadata while non-terminals simply
   * reuse their grammar names. Using a helper keeps {@link #formatProduction(NonTerminalNode)}
   * pleasantly small.
   */
  private static String formatSymbol(ParseNode node) {
    if (node instanceof NonTerminalNode nonTerminal) {
      return nonTerminal.symbol();
    }
    if (node instanceof TerminalNode terminal) {
      return terminal.symbol()
          + "("
          + terminal.line()
          + ","
          + terminal.lexeme()
          + ")";
    }
    return node.symbol();
  }
}

