package hr.fer.ppj.codegen.func;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for traversing declarator nodes to extract variable information.
 *
 * <p>This class provides static methods for extracting variable names and array sizes from
 * declarator AST structures. It handles the complexity of nested declarators and various array
 * declaration patterns.
 *
 * <p><b>Purpose:</b>
 *
 * <p>When processing variable declarations, we need to extract:
 *
 * <ul>
 *   <li>Variable names (IDN terminals)
 *   <li>Array sizes (from {@code L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA} patterns)
 *   <li>Array size expressions (from {@code L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA} patterns)
 * </ul>
 *
 * <p>This class handles the recursive traversal of declarator structures to find these elements,
 * handling nested declarators correctly.
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <p>Handles declarator structures from:
 *
 * <pre>
 * &lt;deklarator&gt; ::= &lt;izravni_deklarator&gt;
 *
 * &lt;izravni_deklarator&gt; ::= IDN
 *                         | &lt;izravni_deklarator&gt; L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
 *                         | &lt;izravni_deklarator&gt; L_UGL_ZAGRADA &lt;izraz&gt; D_UGL_ZAGRADA
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class DeclaratorTraverser {

  /** Private constructor to prevent instantiation. */
  private DeclaratorTraverser() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Extracts the variable name from a direct declarator.
   *
   * <p>Searches for IDN terminals in the declarator structure, handling nested declarators
   * correctly.
   *
   * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
   * @return the variable name (IDN lexeme), or null if not found
   * @throws NullPointerException if directDeclarator is null
   */
  public static String extractVariableName(NonTerminalNode directDeclarator) {
    Objects.requireNonNull(directDeclarator, "directDeclarator must not be null");

    List<ParseNode> children = directDeclarator.children();

    // Handle nested <izravni_deklarator> structure
    NonTerminalNode nestedDeclarator = null;
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<izravni_deklarator>".equals(nonTerminal.symbol())) {
        nestedDeclarator = nonTerminal;
        break;
      }
    }

    // Find IDN (variable name)
    for (ParseNode child : children) {
      if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
        return terminal.lexeme();
      }
    }

    // Handle nested declarator case
    if (nestedDeclarator != null) {
      List<ParseNode> nestedChildren = nestedDeclarator.children();
      for (ParseNode nestedChild : nestedChildren) {
        if (nestedChild instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
          return terminal.lexeme();
        }
      }
    }

    return null;
  }

  /**
   * Checks if a direct declarator represents an array and extracts its size.
   *
   * <p>Looks for the pattern: {@code L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA} or {@code L_UGL_ZAGRADA
   * <izraz> D_UGL_ZAGRADA}
   *
   * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
   * @return array size if found (from BROJ or extracted from expression), 0 if not an array
   */
  public static int extractArraySize(NonTerminalNode directDeclarator) {
    Objects.requireNonNull(directDeclarator, "directDeclarator must not be null");

    List<ParseNode> children = directDeclarator.children();

    // Check for array pattern: L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
    for (int i = 0; i <= children.size() - 3; i++) {
      if (i + 2 < children.size()) {
        ParseNode node1 = children.get(i);
        ParseNode node2 = children.get(i + 1);
        ParseNode node3 = children.get(i + 2);

        if (node1 instanceof TerminalNode t1
            && "L_UGL_ZAGRADA".equals(t1.symbol())
            && node2 instanceof TerminalNode t2
            && "BROJ".equals(t2.symbol())
            && node3 instanceof TerminalNode t3
            && "D_UGL_ZAGRADA".equals(t3.symbol())) {
          try {
            return Integer.parseInt(t2.lexeme());
          } catch (NumberFormatException e) {
            return 0;
          }
        }
      }
    }

    return 0;
  }

  /**
   * Extracts array size from an expression node in an array declarator.
   *
   * <p>Handles the pattern: {@code L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA} by recursively searching
   * for a BROJ terminal in the expression.
   *
   * @param exprNode the expression node ({@code <izraz>})
   * @return the number value as string, or null if not found
   */
  public static String extractNumberFromExpression(NonTerminalNode exprNode) {
    if (exprNode == null) {
      return null;
    }

    for (ParseNode child : exprNode.children()) {
      if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
        return terminal.lexeme();
      } else if (child instanceof NonTerminalNode nonTerminal) {
        String result = extractNumberFromExpression(nonTerminal);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  /**
   * Checks if a direct declarator has a nested declarator.
   *
   * @param directDeclarator the direct declarator node
   * @return the nested declarator if found, or null
   */
  public static NonTerminalNode findNestedDeclarator(NonTerminalNode directDeclarator) {
    Objects.requireNonNull(directDeclarator, "directDeclarator must not be null");

    List<ParseNode> children = directDeclarator.children();
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<izravni_deklarator>".equals(nonTerminal.symbol())) {
        return nonTerminal;
      }
    }
    return null;
  }
}
