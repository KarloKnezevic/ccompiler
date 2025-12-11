package hr.fer.ppj.codegen.global;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;

/**
 * Extracts array size information from the parse tree.
 *
 * <p>This class searches the parse tree to find array size declarations in the form: {@code IDN
 * L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA}.
 *
 * <p>The extractor navigates through the parse tree structure, looking for external declarations
 * and extracting the size value from array declarators.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArraySizeExtractor {

  private final NonTerminalNode parseTree;

  /**
   * Creates a new array size extractor.
   *
   * @param parseTree the parse tree from semantic analysis
   */
  public ArraySizeExtractor(NonTerminalNode parseTree) {
    this.parseTree = parseTree;
  }

  /**
   * Extracts array size from the parse tree by finding the declaration.
   *
   * @param variableName the array variable name
   * @return the array size, or 0 if not found
   */
  public int extractArraySize(String variableName) {
    if (parseTree == null) {
      return 0;
    }
    return extractArraySizeFromNode(parseTree, variableName);
  }

  /** Recursively searches for array size in parse tree nodes. */
  private int extractArraySizeFromNode(NonTerminalNode node, String variableName) {
    String symbol = node.symbol();

    // Look for global variable declarations
    if ("<vanjska_deklaracija>".equals(symbol)) {
      return extractArraySizeFromDeclaration(node, variableName);
    }

    // Recursively search children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nonTerminal) {
        int size = extractArraySizeFromNode(nonTerminal, variableName);
        if (size > 0) {
          return size;
        }
      }
    }

    return 0;
  }

  /**
   * Extracts array size from a declaration node.
   *
   * <p>Looks for the structure:
   *
   * <pre>
   * <deklaracija> -> <lista_init_deklaratora> -> <init_deklarator> ->
   * <deklarator> -> <izravni_deklarator>
   * </pre>
   *
   * In <izravni_deklarator>, looks for: IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
   */
  private int extractArraySizeFromDeclaration(NonTerminalNode declaration, String variableName) {
    return extractArraySizeFromDeclarator(declaration, variableName);
  }

  /** Recursively searches for array size in declarator structure. */
  private int extractArraySizeFromDeclarator(NonTerminalNode node, String variableName) {
    List<ParseNode> children = node.children();

    // Check if this is <izravni_deklarator> with array syntax: IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
    if (children.size() == 4) {
      ParseNode first = children.get(0);
      ParseNode second = children.get(1);
      ParseNode third = children.get(2);
      ParseNode fourth = children.get(3);

      if (first instanceof TerminalNode idToken
          && "IDN".equals(idToken.symbol())
          && second instanceof TerminalNode leftBracket
          && "L_UGL_ZAGRADA".equals(leftBracket.symbol())
          && third instanceof TerminalNode sizeToken
          && "BROJ".equals(sizeToken.symbol())
          && fourth instanceof TerminalNode rightBracket
          && "D_UGL_ZAGRADA".equals(rightBracket.symbol())) {

        // Check if variable name matches
        if (variableName.equals(idToken.lexeme())) {
          try {
            return Integer.parseInt(sizeToken.lexeme());
          } catch (NumberFormatException e) {
            return 0;
          }
        }
      }
    }

    // Recursively search children
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal) {
        int size = extractArraySizeFromDeclarator(nonTerminal, variableName);
        if (size > 0) {
          return size;
        }
      }
    }

    return 0;
  }
}
