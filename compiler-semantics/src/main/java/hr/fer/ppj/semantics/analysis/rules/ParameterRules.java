package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantic rules for function parameter declarations.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Parameter lists</li>
 *   <li>Individual parameter declarations</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class ParameterRules {
  
  private final SemanticChecker checker;
  
  ParameterRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<lista_parametara>", this::visitListaParametara);
    checker.registerRule("<deklaracija_parametra>", this::visitDeklaracijaParametra);
  }
  
  private void visitListaParametara(NonTerminalNode node) {
    var children = node.children();
    if (children.size() == 1) {
      NonTerminalNode param = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(param);
      if (param.attributes().identifier() == null
          && TypeSystem.stripConst(param.attributes().type()) == PrimitiveType.VOID) {
        node.attributes().parameterTypes(List.of());
        node.attributes().parameterNames(List.of());
        return;
      }
      ensureValidParameter(param, node);
      node.attributes().parameterTypes(List.of(param.attributes().type()));
      node.attributes().parameterNames(List.of(param.attributes().identifier()));
      return;
    }
    if (children.size() == 3) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode param = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      checker.visitNonTerminal(param);
      ensureValidParameter(param, node);
      List<Type> types = new ArrayList<>(list.attributes().parameterTypes());
      List<String> names = new ArrayList<>(list.attributes().parameterNames());
      if (names.contains(param.attributes().identifier())) {
        checker.fail(node);
      }
      types.add(param.attributes().type());
      names.add(param.attributes().identifier());
      node.attributes().parameterTypes(types);
      node.attributes().parameterNames(names);
      return;
    }
    checker.fail(node);
  }
  
  private void ensureValidParameter(NonTerminalNode param, NonTerminalNode ctx) {
    if (param.attributes().identifier() == null
        || param.attributes().type() == null
        || TypeSystem.stripConst(param.attributes().type()) == PrimitiveType.VOID) {
      checker.fail(ctx);
    }
  }
  
  /**
   * Performs semantic analysis for function parameter declarations.
   * 
   * <p>Grammar:
   *   <deklaracija_parametra> ::= <ime_tipa> IDN
   *                             | <ime_tipa> IDN L_UGL_ZAGRADA D_UGL_ZAGRADA
   * 
   * <p>This method handles:
   * <ul>
   *   <li>Simple parameters: int x</li>
   *   <li>Array parameters: int arr[] (decays to pointer)</li>
   *   <li>Complex declarators: int *p, struct Node *node</li>
   * </ul>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Parameters cannot have void type (except in void parameter list)</li>
   *   <li>Array parameters decay to pointers in function parameters</li>
   *   <li>Parameters are l-values (can be assigned to within function)</li>
   * </ul>
   * 
   * @param node the parameter declaration node
   */
  private void visitDeklaracijaParametra(NonTerminalNode node) {
    // Process type specification: <ime_tipa>
    NonTerminalNode typeNode = NodeUtils.asNonTerminal(node.children().get(0));
    checker.visitNonTerminal(typeNode);
    Type baseType = typeNode.attributes().type();
    if (baseType == null) {
      checker.fail(node);
    }
    // Handle void parameter (only allowed in void parameter list, no identifier)
    if (node.children().size() == 1) {
      node.attributes().identifier(null);
      node.attributes().type(baseType);
      return;
    }
    // Parameters cannot have void type (except in void parameter list)
    if (TypeSystem.stripConst(baseType) == PrimitiveType.VOID) {
      checker.fail(node);
    }

    ParseNode descriptor = node.children().get(1);
    // Handle simple parameter: <ime_tipa> IDN
    if (descriptor instanceof hr.fer.ppj.semantics.tree.TerminalNode idToken) {
      if (node.children().size() == 2) {
        node.attributes().identifier(idToken.lexeme());
        node.attributes().type(baseType);
        node.attributes().lValue(true);
        return;
      }
      // Handle array parameter: <ime_tipa> IDN L_UGL_ZAGRADA D_UGL_ZAGRADA
      if (node.children().size() == 4) {
        // Arrays decay to pointers in function parameters
        node.attributes().identifier(idToken.lexeme());
        node.attributes().type(new ArrayType(baseType));
        node.attributes().lValue(false);
        return;
      }
      checker.fail(node);
    }

    // Handle complex declarator: <ime_tipa> <deklarator>
    // This handles pointers, nested declarators, etc.
    if (descriptor instanceof NonTerminalNode declarator) {
      declarator.attributes().inheritedType(baseType);
      checker.visitNonTerminal(declarator);
      String identifier = declarator.attributes().identifier();
      Type effectiveType = declarator.attributes().type();
      if (identifier == null || effectiveType == null) {
        checker.fail(node);
      }
      // Copy all attributes from declarator
      node.attributes().identifier(identifier);
      node.attributes().type(effectiveType);
      node.attributes().elementCount(declarator.attributes().elementCount());
      node.attributes().lValue(declarator.attributes().isLValue());
      return;
    }
    checker.fail(node);
  }
}

