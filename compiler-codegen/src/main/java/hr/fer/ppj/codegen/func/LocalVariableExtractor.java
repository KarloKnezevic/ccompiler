package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts local variable information from function body nodes.
 *
 * <p>This class processes compound statements ({@code <slozena_naredba>}) to extract information
 * about local variables, including their names and sizes.
 *
 * <p><b>Grammar Rule:</b> Processes {@code <slozena_naredba>}:
 *
 * <pre>
 * &lt;slozena_naredba&gt; ::= L_VIT_ZAGRADA &lt;lista_deklaracija&gt; &lt;lista_naredbi&gt; D_VIT_ZAGRADA
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LocalVariableExtractor {

  /** Information about a local variable including its name and size. */
  public record VariableInfo(String name, int sizeInBytes) {}

  private final StructArraySizeExtractor arraySizeExtractor;
  private final VariableSizeCalculator sizeCalculator;

  /**
   * Creates a new local variable extractor.
   *
   * @param parseTree the parse tree from semantic analysis (for extracting struct array sizes)
   */
  public LocalVariableExtractor(NonTerminalNode parseTree) {
    this.arraySizeExtractor = new StructArraySizeExtractor(parseTree);
    this.sizeCalculator = new VariableSizeCalculator(arraySizeExtractor);
  }

  /**
   * Extracts local variable information from a function body.
   *
   * @param body the function body node ({@code <slozena_naredba>})
   * @param elementSize the element size in bytes (always 4 for this project)
   * @return list of variable information (name and size in bytes)
   */
  public List<VariableInfo> extractLocalVariables(NonTerminalNode body, int elementSize) {
    Objects.requireNonNull(body, "body must not be null");

    List<VariableInfo> variables = new ArrayList<>();
    findLocalVariables(body, variables, elementSize);
    return variables;
  }

  /**
   * Recursively finds local variable declarations in the parse tree.
   *
   * @param node the node to search in
   * @param variables the list to add variable information to
   * @param elementSize the element size in bytes (always 4 for this project)
   */
  private void findLocalVariables(
      NonTerminalNode node, List<VariableInfo> variables, int elementSize) {
    String symbol = node.symbol();

    if ("<lista_deklaracija>".equals(symbol)) {
      processDeclarationList(node, variables, elementSize);
    } else if ("<slozena_naredba>".equals(symbol)) {
      for (ParseNode child : node.children()) {
        if (child instanceof NonTerminalNode nonTerminal) {
          String childSymbol = nonTerminal.symbol();
          if ("<lista_deklaracija>".equals(childSymbol)) {
            processDeclarationList(nonTerminal, variables, elementSize);
          } else {
            findLocalVariables(nonTerminal, variables, elementSize);
          }
        }
      }
    } else {
      for (ParseNode child : node.children()) {
        if (child instanceof NonTerminalNode nonTerminal) {
          findLocalVariables(nonTerminal, variables, elementSize);
        }
      }
    }
  }

  /**
   * Processes a declaration list and extracts variable information.
   *
   * @param declarationList the declaration list node ({@code <lista_deklaracija>})
   * @param variables the list to add variable information to
   * @param elementSize the element size in bytes (always 4 for this project)
   */
  private void processDeclarationList(
      NonTerminalNode declarationList, List<VariableInfo> variables, int elementSize) {
    List<ParseNode> children = declarationList.children();

    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal) {
        String childSymbol = nonTerminal.symbol();

        if ("<lista_deklaracija>".equals(childSymbol)) {
          processDeclarationList(nonTerminal, variables, elementSize);
        } else if ("<deklaracija>".equals(childSymbol)) {
          List<VariableInfo> declVars = extractVariableInfo(nonTerminal, elementSize);
          variables.addAll(declVars);
        }
      }
    }
  }

  /**
   * Extracts variable information (name and size) from a declaration node.
   *
   * @param declaration the declaration node ({@code <deklaracija>})
   * @param elementSize the element size in bytes (always 4 for this project)
   * @return list of variable information (name and size in bytes)
   */
  private List<VariableInfo> extractVariableInfo(NonTerminalNode declaration, int elementSize) {
    List<VariableInfo> variables = new ArrayList<>();

    // Extract base type from <ime_tipa> node
    Type baseType = null;
    for (ParseNode child : declaration.children()) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<ime_tipa>".equals(nonTerminal.symbol())) {
        if (nonTerminal.attributes() != null) {
          baseType = nonTerminal.attributes().type();
        }
        break;
      }
    }

    for (ParseNode child : declaration.children()) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
        // Pass base type to the list extractor
        extractVariableInfoFromList(nonTerminal, variables, elementSize, baseType);
      }
    }

    return variables;
  }

  /**
   * Extracts variable information from a list of init declarators.
   *
   * @param list the list of init declarators ({@code <lista_init_deklaratora>})
   * @param variables the list to add variable info to
   * @param elementSize the element size in bytes (always 4 for this project)
   * @param baseType the base type from the declaration (may be null)
   */
  private void extractVariableInfoFromList(
      NonTerminalNode list, List<VariableInfo> variables, int elementSize, Type baseType) {
    List<ParseNode> children = list.children();

    // Get inherited type from semantic attributes if available
    Type inheritedType = baseType;
    if (list.attributes() != null && list.attributes().inheritedType() != null) {
      inheritedType = list.attributes().inheritedType();
    }

    if (children.size() == 1) {
      extractVariableInfoFromInitDeclarator(
          (NonTerminalNode) children.get(0), variables, elementSize, inheritedType);
    } else if (children.size() == 3) {
      extractVariableInfoFromList(
          (NonTerminalNode) children.get(0), variables, elementSize, inheritedType);
      extractVariableInfoFromInitDeclarator(
          (NonTerminalNode) children.get(2), variables, elementSize, inheritedType);
    }
  }

  /**
   * Extracts variable information from an init declarator.
   *
   * @param initDeclarator the init declarator node ({@code <init_deklarator>})
   * @param variables the list to add variable info to
   * @param elementSize the element size in bytes (always 4 for this project)
   * @param inheritedType the inherited type from the declaration (may be null)
   */
  private void extractVariableInfoFromInitDeclarator(
      NonTerminalNode initDeclarator,
      List<VariableInfo> variables,
      int elementSize,
      Type inheritedType) {
    // Try to get type from semantic attributes
    Type variableType = inheritedType;
    if (initDeclarator.attributes() != null
        && initDeclarator.attributes().inheritedType() != null) {
      variableType = initDeclarator.attributes().inheritedType();
    }

    for (ParseNode child : initDeclarator.children()) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<deklarator>".equals(nonTerminal.symbol())) {
        // Get type from declarator if not already available
        if (variableType == null && nonTerminal.attributes() != null) {
          variableType = nonTerminal.attributes().type();
        }
        extractVariableInfoFromDeclarator(nonTerminal, variables, elementSize, variableType);
      }
    }
  }

  /**
   * Extracts variable information from a declarator.
   *
   * @param declarator the declarator node ({@code <deklarator>})
   * @param variables the list to add variable info to
   * @param elementSize the element size in bytes (always 4 for this project)
   * @param inheritedType the inherited type from the declaration (may be null)
   */
  private void extractVariableInfoFromDeclarator(
      NonTerminalNode declarator,
      List<VariableInfo> variables,
      int elementSize,
      Type inheritedType) {
    // Try to get type from semantic attributes
    Type variableType = inheritedType;
    if (declarator.attributes() != null) {
      if (declarator.attributes().type() != null) {
        variableType = declarator.attributes().type();
      } else if (declarator.attributes().inheritedType() != null) {
        variableType = declarator.attributes().inheritedType();
      }
    }

    for (ParseNode child : declarator.children()) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<izravni_deklarator>".equals(nonTerminal.symbol())) {
        extractVariableInfoFromDirectDeclarator(nonTerminal, variables, elementSize, variableType);
      }
    }
  }

  /**
   * Extracts variable information from a direct declarator.
   *
   * <p>Handles both simple variables and arrays, extracting array sizes from expression nodes when
   * present. Also handles struct types by calculating their size.
   *
   * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
   * @param variables the list to add variable info to
   * @param elementSize the element size in bytes (always 4 for this project)
   * @param variableType the type of the variable (from semantic attributes, may be null)
   */
  private void extractVariableInfoFromDirectDeclarator(
      NonTerminalNode directDeclarator,
      List<VariableInfo> variables,
      int elementSize,
      Type variableType) {
    // Check for nested declarator first
    NonTerminalNode nestedDeclarator = DeclaratorTraverser.findNestedDeclarator(directDeclarator);
    if (nestedDeclarator != null) {
      // Handle nested declarator with array brackets after it
      List<ParseNode> children = directDeclarator.children();
      int nestedIndex = -1;
      for (int i = 0; i < children.size(); i++) {
        if (children.get(i) == nestedDeclarator) {
          nestedIndex = i;
          break;
        }
      }

      // Check for array brackets after nested declarator: <izravni_deklarator> L_UGL_ZAGRADA
      // <izraz> D_UGL_ZAGRADA
      if (nestedIndex >= 0 && nestedIndex + 3 < children.size()) {
        ParseNode node1 = children.get(nestedIndex + 1);
        ParseNode node2 = children.get(nestedIndex + 2);
        ParseNode node3 = children.get(nestedIndex + 3);
        if (node1 instanceof TerminalNode t1
            && "L_UGL_ZAGRADA".equals(t1.symbol())
            && node2 instanceof NonTerminalNode exprNode
            && node3 instanceof TerminalNode t3
            && "D_UGL_ZAGRADA".equals(t3.symbol())) {
          String numberValue = DeclaratorTraverser.extractNumberFromExpression(exprNode);
          if (numberValue != null) {
            try {
              int arraySize = Integer.parseInt(numberValue);
              String varName = DeclaratorTraverser.extractVariableName(nestedDeclarator);
              if (varName != null) {
                int size = sizeCalculator.calculateSize(variableType, arraySize, elementSize);
                variables.add(new VariableInfo(varName, size));
                return;
              }
            } catch (NumberFormatException e) {
              // Ignore, continue processing
            }
          }
        }
      }

      // Try to extract from nested declarator recursively
      String varName = DeclaratorTraverser.extractVariableName(nestedDeclarator);
      if (varName != null) {
        int arraySize = DeclaratorTraverser.extractArraySize(nestedDeclarator);
        if (arraySize > 0) {
          int size = sizeCalculator.calculateSize(variableType, arraySize, elementSize);
          variables.add(new VariableInfo(varName, size));
        } else {
          int size = sizeCalculator.calculateSize(variableType, 0, elementSize);
          variables.add(new VariableInfo(varName, size));
        }
        return;
      }

      // Recurse into nested declarator
      extractVariableInfoFromDirectDeclarator(
          nestedDeclarator, variables, elementSize, variableType);
      return;
    }

    // No nested declarator - extract from this declarator directly
    String varName = DeclaratorTraverser.extractVariableName(directDeclarator);
    if (varName == null) {
      return;
    }

    int arraySize = DeclaratorTraverser.extractArraySize(directDeclarator);
    if (arraySize > 0) {
      // Array variable
      int size = sizeCalculator.calculateSize(variableType, arraySize, elementSize);
      variables.add(new VariableInfo(varName, size));
    } else {
      // Simple variable
      int size = sizeCalculator.calculateSize(variableType, 0, elementSize);
      variables.add(new VariableInfo(varName, size));
    }
  }
}
