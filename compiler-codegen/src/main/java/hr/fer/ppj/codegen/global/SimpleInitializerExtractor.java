package hr.fer.ppj.codegen.global;

import hr.fer.ppj.codegen.utils.ConstantValueExtractor;
import hr.fer.ppj.codegen.utils.IdentifierExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;

/**
 * Extracts initializer values for simple (non-array) global variables.
 *
 * <p>This class searches the parse tree to find initializer values for simple global variables,
 * handling integer and character constants, including negative literals and escape sequences.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SimpleInitializerExtractor {

  private final NonTerminalNode parseTree;

  /**
   * Creates a new simple initializer extractor.
   *
   * @param parseTree the parse tree from semantic analysis
   */
  public SimpleInitializerExtractor(NonTerminalNode parseTree) {
    this.parseTree = parseTree;
  }

  /**
   * Finds the initializer value for a global variable from the parse tree.
   *
   * @param variableName the name of the variable to find
   * @param isChar whether the variable is of char type
   * @return the initializer value as string, or null if not found
   */
  public String findInitializerValue(String variableName, boolean isChar) {
    if (parseTree == null) {
      return null;
    }
    return findInitializerInNode(parseTree, variableName, isChar);
  }

  /** Recursively searches for variable initializer in parse tree nodes. */
  private String findInitializerInNode(NonTerminalNode node, String variableName, boolean isChar) {
    String symbol = node.symbol();

    if ("<vanjska_deklaracija>".equals(symbol)) {
      return findInitializerInDeclaration(node, variableName, isChar);
    }

    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nonTerminal) {
        String result = findInitializerInNode(nonTerminal, variableName, isChar);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  /** Searches for variable initializer in a declaration node. */
  private String findInitializerInDeclaration(
      NonTerminalNode declaration, String variableName, boolean isChar) {
    for (ParseNode child : declaration.children()) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<deklaracija>".equals(nonTerminal.symbol())) {
        return findInitializerInVariableDeclaration(nonTerminal, variableName, isChar);
      }
    }
    return null;
  }

  /** Searches for variable initializer in a variable declaration. */
  private String findInitializerInVariableDeclaration(
      NonTerminalNode declaration, String variableName, boolean isChar) {
    for (ParseNode child : declaration.children()) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
        return findInitializerInInitDeclaratorList(nonTerminal, variableName, isChar);
      }
    }
    return null;
  }

  /** Searches for variable initializer in init declarator list. */
  private String findInitializerInInitDeclaratorList(
      NonTerminalNode list, String variableName, boolean isChar) {
    var children = list.children();

    if (children.size() == 1) {
      return findInitializerInInitDeclarator(
          (NonTerminalNode) children.get(0), variableName, isChar);
    } else if (children.size() == 3) {
      String result =
          findInitializerInInitDeclaratorList(
              (NonTerminalNode) children.get(0), variableName, isChar);
      if (result != null) return result;
      return findInitializerInInitDeclarator(
          (NonTerminalNode) children.get(2), variableName, isChar);
    }

    return null;
  }

  /** Searches for variable initializer in init declarator. */
  private String findInitializerInInitDeclarator(
      NonTerminalNode declarator, String variableName, boolean isChar) {
    var children = declarator.children();

    if (children.size() == 3) {
      ParseNode operator = children.get(1);
      if (operator instanceof TerminalNode terminal && "OP_PRIDRUZI".equals(terminal.symbol())) {
        String declaredName = extractVariableName((NonTerminalNode) children.get(0));
        if (variableName.equals(declaredName)) {
          return extractInitializerValue((NonTerminalNode) children.get(2), isChar);
        }
      }
    }

    return null;
  }

  /** Extracts variable name from declarator. */
  private String extractVariableName(NonTerminalNode declarator) {
    return IdentifierExtractor.findIdentifier(declarator);
  }

  /** Extracts initializer value from inicijalizator node. */
  private String extractInitializerValue(NonTerminalNode initializer, boolean isChar) {
    return ConstantValueExtractor.findConstantValue(initializer, isChar);
  }
}
