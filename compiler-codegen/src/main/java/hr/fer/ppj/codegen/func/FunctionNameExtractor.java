package hr.fer.ppj.codegen.func;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Extracts function names from parse tree nodes.
 *
 * <p>This class handles navigation through the parse tree structure to find function identifiers in
 * declarator nodes.
 *
 * <p><b>Grammar Rule:</b> Navigates through {@code <deklarator> -> <izravni_deklarator> -> IDN}
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionNameExtractor {

  /**
   * Extracts the function name from a deklarator node.
   *
   * @param deklarator the deklarator node ({@code <deklarator>})
   * @return the function name (IDN lexeme), or null if not found
   */
  public String extractFunctionName(NonTerminalNode deklarator) {
    Objects.requireNonNull(deklarator, "deklarator must not be null");

    // Navigate through <deklarator> -> <izravni_deklarator> -> <izravni_deklarator> -> IDN
    List<ParseNode> children = deklarator.children();

    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<izravni_deklarator>".equals(nonTerminal.symbol())) {
        return extractFunctionNameFromDirectDeclarator(nonTerminal);
      }
    }

    return null;
  }

  /**
   * Extracts the function name from an izravni_deklarator node.
   *
   * <p>Handles both direct and nested {@code <izravni_deklarator>} structures.
   *
   * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
   * @return the function name (IDN lexeme), or null if not found
   */
  private String extractFunctionNameFromDirectDeclarator(NonTerminalNode directDeclarator) {
    List<ParseNode> children = directDeclarator.children();

    for (ParseNode child : children) {
      if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
        return terminal.lexeme();
      } else if (child instanceof NonTerminalNode nonTerminal
          && "<izravni_deklarator>".equals(nonTerminal.symbol())) {
        // Recursive case for nested izravni_deklarator
        String name = extractFunctionNameFromDirectDeclarator(nonTerminal);
        if (name != null) {
          return name;
        }
      }
    }

    return null;
  }
}
