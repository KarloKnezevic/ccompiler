package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;

/**
 * Utility class for extracting identifiers from parse tree nodes.
 *
 * <p>This class provides methods to recursively search parse tree nodes for identifier terminals
 * (IDN tokens), eliminating duplication across multiple extractor and generator classes.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IdentifierExtractor {

  private IdentifierExtractor() {
    // Utility class - prevent instantiation
  }

  /**
   * Recursively searches for an identifier in a parse tree node.
   *
   * <p>This method traverses the parse tree depth-first, looking for terminal nodes with symbol
   * "IDN" and returns the lexeme (identifier name).
   *
   * @param node the parse tree node to search
   * @return the identifier name (lexeme), or null if not found
   */
  public static String findIdentifier(NonTerminalNode node) {
    for (ParseNode child : node.children()) {
      if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
        return terminal.lexeme();
      } else if (child instanceof NonTerminalNode nonTerminal) {
        String result = findIdentifier(nonTerminal);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }
}
