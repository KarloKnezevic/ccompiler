package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantic rules for declarators (direct declarators, arrays, functions, pointers).
 * 
 * <p>Handles:
 * <ul>
 *   <li>Direct declarators (identifiers, arrays, functions)</li>
 *   <li>Declarators with pointer modifiers</li>
 *   <li>Nested declarators</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class DeclaratorRules {
  
  private final SemanticChecker checker;
  
  DeclaratorRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<izravni_deklarator>", this::visitIzravniDeklarator);
    checker.registerRule("<deklarator>", this::visitDeklarator);
  }
  
  /**
   * Performs semantic analysis for direct declarators.
   * 
   * <p>Grammar:
   *   <izravni_deklarator> ::= IDN
   *                          | IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
   *                          | IDN L_ZAGRADA KR_VOID D_ZAGRADA
   *                          | IDN L_ZAGRADA <lista_parametara> D_ZAGRADA
   *                          | <deklarator> L_ZAGRADA ...
   *                          | <deklarator> L_UGL_ZAGRADA ...
   * 
   * <p>This method handles complex declarator parsing including:
   * <ul>
   *   <li>Simple identifiers</li>
   *   <li>Array declarators (sized and unsized)</li>
   *   <li>Function declarators</li>
   *   <li>Nested declarators (pointers, arrays, functions)</li>
   * </ul>
   * 
   * @param node the direct declarator node
   */
  private void visitIzravniDeklarator(NonTerminalNode node) {
    var children = node.children();
    if (children.isEmpty()) {
      checker.fail(node);
    }
    // Case 1: Simple identifier: IDN
    if (children.size() == 1 && children.get(0) instanceof TerminalNode id) {
      Type inherited = node.attributes().inheritedType();
      if (inherited == null) {
        checker.fail(node);
      }
      // Simple variable: type is inherited from parent
      node.attributes().identifier(id.lexeme());
      node.attributes().type(inherited);
      node.attributes().lValue(true);
      return;
    }
    ParseNode first = children.get(0);
    // Case 2: Nested declarator (pointer, array, or function)
    if (first instanceof NonTerminalNode inner && children.size() > 1) {
      inner.attributes().inheritedType(node.attributes().inheritedType());
      checker.visitNonTerminal(inner);
      // Check for nested function declarator: <deklarator> L_ZAGRADA ...
      if (children.get(1) instanceof TerminalNode lParen
          && "L_ZAGRADA".equals(lParen.symbol())) {
        handleNestedFunctionDeclarator(node, inner, children);
        return;
      }
      // Check for unsized array: <deklarator> L_UGL_ZAGRADA D_UGL_ZAGRADA
      if (isUnsizedArrayDeclarator(children)) {
        Type baseType = inner.attributes().type();
        if (baseType == null) {
          checker.fail(node);
        }
        // Unsized array: size is determined by initializer (empty dimensions)
        node.attributes().identifier(inner.attributes().identifier());
        Type elementType = baseType instanceof ArrayType arr ? arr.elementType() : baseType;
        List<Integer> dimensions = baseType instanceof ArrayType arr ? new ArrayList<>(arr.dimensions()) : new ArrayList<>();
        dimensions.add(0, 0); // 0 means unsized
        node.attributes().type(new ArrayType(elementType, dimensions));
        node.attributes().lValue(false);
        return;
      }
      // Check for sized array: <deklarator> L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
      if (isSizedArrayDeclarator(children)) {
        Type baseType = inner.attributes().type();
        if (baseType == null) {
          checker.fail(node);
        }
        // Extract array size from literal
        String literal = extractArrayLengthLiteral(children.get(2), node);
        int length = checker.parseArrayLength(literal, node);
        node.attributes().identifier(inner.attributes().identifier());
        
        // Build dimensions list: if baseType is already an ArrayType, prepend new dimension
        List<Integer> dimensions = new ArrayList<>();
        if (baseType instanceof ArrayType nestedArray) {
          dimensions.addAll(nestedArray.dimensions());
        }
        dimensions.add(0, length); // Add outermost dimension first
        
        node.attributes().type(new ArrayType(baseType instanceof ArrayType arr ? arr.elementType() : baseType, dimensions));
        node.attributes().elementCount(length);
        node.attributes().lValue(false);
        return;
      }
      node.attributes().identifier(inner.attributes().identifier());
      node.attributes().type(inner.attributes().type());
      node.attributes().parameterTypes(inner.attributes().parameterTypes());
      node.attributes().parameterNames(inner.attributes().parameterNames());
      node.attributes().elementCount(inner.attributes().elementCount());
      node.attributes().lValue(inner.attributes().isLValue());
      return;
    }
    // Case 3: Array or function declarator starting with identifier
    if (first instanceof TerminalNode idToken) {
      // Array declarator: IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
      if (children.size() == 4 && "L_UGL_ZAGRADA".equals(((TerminalNode) children.get(1)).symbol())) {
        handleArrayDeclarator(node, idToken, children);
        return;
      }
      // Function declarator: IDN L_ZAGRADA ... D_ZAGRADA
      handleFunctionDeclarator(node, idToken, children);
      return;
    }
    if (first instanceof NonTerminalNode inner) {
      inner.attributes().inheritedType(node.attributes().inheritedType());
      checker.visitNonTerminal(inner);
      node.attributes().identifier(inner.attributes().identifier());
      node.attributes().type(inner.attributes().type());
      node.attributes().parameterTypes(inner.attributes().parameterTypes());
      node.attributes().parameterNames(inner.attributes().parameterNames());
      node.attributes().elementCount(inner.attributes().elementCount());
      node.attributes().lValue(inner.attributes().isLValue());
      return;
    }
    checker.fail(node);
  }
  
  private void handleArrayDeclarator(
      NonTerminalNode node, TerminalNode idToken, List<ParseNode> children) {
    Type inherited = node.attributes().inheritedType();
    if (inherited == null) {
      checker.fail(node);
    }
    if (TypeSystem.stripConst(inherited).isVoid()) {
      checker.fail(node);
    }
    String literal = extractArrayLengthLiteral(children.get(2), node);
    int length = checker.parseArrayLength(literal, node);
    node.attributes().identifier(idToken.lexeme());
    
    // For single-dimensional arrays, create with dimension
    node.attributes().type(new ArrayType(inherited, length));
    node.attributes().elementCount(length);
    node.attributes().lValue(false);
  }
  
  /**
   * Handles function declarator parsing.
   * 
   * <p>Grammar:
   *   IDN L_ZAGRADA KR_VOID D_ZAGRADA
   *   IDN L_ZAGRADA <lista_parametara> D_ZAGRADA
   * 
   * <p>This method:
   * <ol>
   *   <li>Validates function declarator syntax</li>
   *   <li>Processes parameter list (or void for no parameters)</li>
   *   <li>Builds function type from return type and parameters</li>
   * </ol>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Return type must be provided (inherited attribute)</li>
   *   <li>Void parameter list means no parameters</li>
   *   <li>Function type is built from return type and parameter types</li>
   * </ul>
   * 
   * @param node the direct declarator node
   * @param idToken the function identifier token
   * @param children all children of the declarator node
   */
  private void handleFunctionDeclarator(
      NonTerminalNode node, TerminalNode idToken, List<ParseNode> children) {
    Type inherited = node.attributes().inheritedType();
    if (inherited == null) {
      checker.fail(node);
    }
    if (children.size() < 4) {
      checker.fail(node);
    }
    // Validate function declarator syntax: IDN L_ZAGRADA ... D_ZAGRADA
    if (!(children.get(1) instanceof TerminalNode lParen)
        || !"L_ZAGRADA".equals(lParen.symbol())
        || !(children.get(children.size() - 1) instanceof TerminalNode rParen)
        || !"D_ZAGRADA".equals(rParen.symbol())) {
      checker.fail(node);
    }

    // Process parameter list
    List<Type> parameterTypes = List.of();
    List<String> parameterNames = List.of();
    // Check for void parameter list: IDN L_ZAGRADA KR_VOID D_ZAGRADA
    if (children.size() == 4 && children.get(2) instanceof TerminalNode voidToken) {
      if (!SemanticConstants.KR_VOID.equals(voidToken.symbol())) {
        checker.fail(node);
      }
      // Void parameter list means no parameters
    } else {
      // Process parameter list: IDN L_ZAGRADA <lista_parametara> D_ZAGRADA
      NonTerminalNode params = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(params);
      parameterTypes = params.attributes().parameterTypes();
      parameterNames = params.attributes().parameterNames();
    }

    // Build function type: return type + parameter types
    node.attributes().identifier(idToken.lexeme());
    node.attributes().parameterTypes(parameterTypes);
    node.attributes().parameterNames(parameterNames);
    node.attributes().type(new FunctionType(inherited, parameterTypes));
    node.attributes().lValue(false);
  }
  
  private void handleNestedFunctionDeclarator(
      NonTerminalNode node, NonTerminalNode inner, List<ParseNode> children) {
    if (children.size() != 4) {
      checker.fail(node);
    }
    List<Type> parameterTypes = List.of();
    List<String> parameterNames = List.of();
    ParseNode paramsNode = children.get(2);
    if (paramsNode instanceof TerminalNode voidToken) {
      if (!SemanticConstants.KR_VOID.equals(voidToken.symbol())) {
        checker.fail(node);
      }
    } else {
      NonTerminalNode params = NodeUtils.asNonTerminal(paramsNode);
      checker.visitNonTerminal(params);
      parameterTypes = params.attributes().parameterTypes();
      parameterNames = params.attributes().parameterNames();
    }
    node.attributes().identifier(inner.attributes().identifier());
    node.attributes().parameterTypes(parameterTypes);
    node.attributes().parameterNames(parameterNames);
    node.attributes().type(new FunctionType(inner.attributes().type(), parameterTypes));
    node.attributes().lValue(false);
  }
  
  private void visitDeklarator(NonTerminalNode node) {
    var children = node.children();
    Type baseType = node.attributes().inheritedType();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    
    if (children.size() == 1) {
      NonTerminalNode inner = NodeUtils.asNonTerminal(children.get(0));
      inner.attributes().inheritedType(baseType);
      checker.visitNonTerminal(inner);
      node.attributes().identifier(inner.attributes().identifier());
      node.attributes().type(inner.attributes().type());
      node.attributes().parameterTypes(inner.attributes().parameterTypes());
      node.attributes().parameterNames(inner.attributes().parameterNames());
      node.attributes().elementCount(inner.attributes().elementCount());
      node.attributes().lValue(inner.attributes().isLValue());
    } else if (children.size() == 2) {
      NonTerminalNode pointer = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode declarator = NodeUtils.asNonTerminal(children.get(1));
      
      pointer.attributes().inheritedType(baseType);
      checker.visitNonTerminal(pointer);
      Type pointerType = pointer.attributes().type();
      if (pointerType == null) {
        checker.fail(node);
        return;
      }
      
      declarator.attributes().inheritedType(pointerType);
      checker.visitNonTerminal(declarator);
      node.attributes().identifier(declarator.attributes().identifier());
      node.attributes().type(declarator.attributes().type());
      node.attributes().parameterTypes(declarator.attributes().parameterTypes());
      node.attributes().parameterNames(declarator.attributes().parameterNames());
      node.attributes().elementCount(declarator.attributes().elementCount());
      node.attributes().lValue(declarator.attributes().isLValue());
    } else {
      checker.fail(node);
    }
  }
  
  private boolean isUnsizedArrayDeclarator(List<ParseNode> children) {
    return children.size() == 3
        && isTerminal(children.get(1), "L_UGL_ZAGRADA")
        && isTerminal(children.get(2), "D_UGL_ZAGRADA");
  }

  private boolean isSizedArrayDeclarator(List<ParseNode> children) {
    return children.size() == 4
        && isTerminal(children.get(1), "L_UGL_ZAGRADA")
        && isTerminal(children.get(3), "D_UGL_ZAGRADA");
  }

  private boolean isTerminal(ParseNode node, String symbol) {
    return node instanceof TerminalNode terminal && symbol.equals(terminal.symbol());
  }

  private String extractArrayLengthLiteral(ParseNode node, NonTerminalNode ctx) {
    if (node instanceof TerminalNode terminal) {
      if (!"BROJ".equals(terminal.symbol())) {
        checker.fail(ctx);
      }
      return terminal.lexeme();
    }
    if (node instanceof NonTerminalNode nonTerminal) {
      var children = nonTerminal.children();
      if (children.size() == 1) {
        return extractArrayLengthLiteral(children.get(0), ctx);
      }
    }
    checker.fail(ctx);
    return "";
  }
}

