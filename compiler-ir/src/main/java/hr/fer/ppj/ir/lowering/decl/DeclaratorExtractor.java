package hr.fer.ppj.ir.lowering.decl;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Extracts declarator information from AST nodes.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class DeclaratorExtractor {

  private DeclaratorExtractor() {}

  /**
   * Extracts the base type from a declaration node.
   */
  public static Type extractBaseType(List<ParseNode> children, NonTerminalNode node) {
    Objects.requireNonNull(children, "children must not be null");
    Objects.requireNonNull(node, "node must not be null");

    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt) {
        String symbol = nt.symbol();
        if (symbol.equals("<ime_tipa>") || symbol.equals("<specifikatori_deklaracije>")) {
          Type type = nt.attributes().type();
          if (type != null) {
            return type;
          }
        }
      }
    }
    return node.attributes().type();
  }

  /**
   * Finds the init declarator list node in children.
   */
  public static NonTerminalNode findInitDeclaratorList(List<ParseNode> children) {
    Objects.requireNonNull(children, "children must not be null");
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt && nt.symbol().equals("<lista_init_deklaratora>")) {
        return nt;
      }
    }
    return null;
  }

  /**
   * Finds the init declarator node in children.
   */
  public static NonTerminalNode findInitDeclarator(List<ParseNode> children) {
    Objects.requireNonNull(children, "children must not be null");
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt && nt.symbol().equals("<init_deklarator>")) {
        return nt;
      }
    }
    return null;
  }

  /**
   * Finds the declarator node in children.
   */
  public static NonTerminalNode findDeclarator(List<ParseNode> children) {
    Objects.requireNonNull(children, "children must not be null");
    if (children.isEmpty()) {
      return null;
    }
    ParseNode firstChild = children.get(0);
    if (firstChild instanceof NonTerminalNode nt) {
      String symbol = nt.symbol();
      if (symbol.equals("<izravni_deklarator>") || symbol.equals("<deklarator>")) {
        return nt;
      }
    }
    return null;
  }

  /**
   * Extracts the final declarator (may unwrap <deklarator> to get <izravni_deklarator>).
   */
  public static NonTerminalNode extractFinalDeclarator(NonTerminalNode declarator) {
    Objects.requireNonNull(declarator, "declarator must not be null");
    if (declarator.symbol().equals("<deklarator>")) {
      for (ParseNode child : declarator.children()) {
        if (child instanceof NonTerminalNode nt
            && nt.symbol().equals("<izravni_deklarator>")) {
          return nt;
        }
      }
    }
    return declarator;
  }

  /**
   * Extracts the variable name from declarator nodes.
   */
  public static String extractVariableName(
      NonTerminalNode finalDeclarator, NonTerminalNode originalDeclarator) {
    Objects.requireNonNull(finalDeclarator, "finalDeclarator must not be null");
    Objects.requireNonNull(originalDeclarator, "originalDeclarator must not be null");

    String varName = finalDeclarator.attributes().identifier();
    if (varName == null && originalDeclarator.symbol().equals("<deklarator>")) {
      varName = originalDeclarator.attributes().identifier();
    }
    return varName;
  }

  /**
   * Extracts the variable type from declarator nodes.
   */
  public static Type extractVariableType(
      NonTerminalNode finalDeclarator, NonTerminalNode originalDeclarator, Type baseType) {
    Objects.requireNonNull(finalDeclarator, "finalDeclarator must not be null");
    Objects.requireNonNull(originalDeclarator, "originalDeclarator must not be null");

    Type varType = finalDeclarator.attributes().type();
    if (varType == null) {
      varType = baseType;
    }
    if (varType == null && originalDeclarator.symbol().equals("<deklarator>")) {
      varType = originalDeclarator.attributes().type();
      if (varType == null) {
        varType = baseType;
      }
    }
    return varType;
  }
}
