package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;

/**
 * Semantic rules for postfix expressions.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Array indexing</li>
 *   <li>Function calls</li>
 *   <li>Struct member access</li>
 *   <li>Postfix increment/decrement</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class PostfixExpressionRules {
  
  private final SemanticChecker checker;
  
  PostfixExpressionRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<postfiks_izraz>", this::visitPostfiksIzraz);
  }
  
  private void visitPostfiksIzraz(NonTerminalNode node) {
    var children = node.children();
    
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    
    NonTerminalNode base = NodeUtils.asNonTerminal(children.get(0));
    checker.visitNonTerminal(base);
    TerminalNode op = (TerminalNode) children.get(1);
    
    switch (op.symbol()) {
      case "L_UGL_ZAGRADA" -> handleArrayElement(node, base, children);
      case "L_ZAGRADA" -> handleFunctionCall(node, base, children);
      case SemanticConstants.TOCKA -> handleStructMemberAccess(node, base, children);
      case SemanticConstants.OP_INC, SemanticConstants.OP_DEC -> handlePostfixIncDec(node, base);
      default -> checker.fail(node);
    }
  }
  
  /**
   * Handles array indexing: array[index].
   * 
   * <p>Grammar:
   *   <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Base must be an array type (or pointer type after array decay)</li>
   *   <li>Index must be an integer type (int, char, or convertible to int)</li>
   *   <li>Result type is the element type</li>
   *   <li>Result is an l-value if element type is not const</li>
   * </ul>
   * 
   * @param node the postfix expression node
   * @param base the base expression (array)
   * @param children all children of the postfix expression node
   */
  private void handleArrayElement(
      NonTerminalNode node, NonTerminalNode base, List<ParseNode> children) {
    Type baseType = base.attributes().type();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    
    // Process index expression: <izraz>
    NonTerminalNode index = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(index);
    // Index must be an integer type (int, char, or convertible to int)
    checker.ensureIntConvertible(index.attributes().type(), node);
    
    // Base must be an array type (arrays decay to pointers in most contexts, but indexing
    // is one context where we need the array type itself)
    Type stripped = TypeSystem.stripConst(baseType);
    if (!(stripped instanceof ArrayType arrayType)) {
      checker.fail(node);
      return;
    }
    
    // Result type is the element type
    Type elementType = arrayType.elementType();
    node.attributes().type(elementType);
    // Result is an l-value if element type is not const (can be assigned to)
    node.attributes().lValue(!TypeSystem.isConst(elementType));
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  /**
   * Handles function calls: func() or func(arg1, arg2, ...).
   * 
   * <p>Grammar:
   *   <postfiks_izraz> L_ZAGRADA D_ZAGRADA
   *   <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Base must be a function type</li>
   *   <li>Argument count must match parameter count</li>
   *   <li>Each argument type must be assignable to corresponding parameter type</li>
   *   <li>Result type is the function return type</li>
   *   <li>Result is not an l-value (function calls are r-values)</li>
   * </ul>
   * 
   * @param node the postfix expression node
   * @param base the base expression (function)
   * @param children all children of the postfix expression node
   */
  private void handleFunctionCall(
      NonTerminalNode node, NonTerminalNode base, List<ParseNode> children) {
    Type type = base.attributes().type();
    Type stripped = TypeSystem.stripConst(type);
    // Base must be a function type
    if (!(stripped instanceof FunctionType functionType)) {
      checker.fail(node);
      return;
    }
    
    // Process argument list if present
    List<Type> arguments = List.of();
    if (children.size() == 4) {
      // Arguments present: <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      arguments = list.attributes().parameterTypes();
    }
    // Otherwise: <postfiks_izraz> L_ZAGRADA D_ZAGRADA (no arguments)
    
    // Validate argument count matches parameter count
    List<Type> params = functionType.parameterTypes();
    if (params.size() != arguments.size()) {
      checker.fail(node);
    }
    // Validate each argument type is assignable to corresponding parameter type
    // This allows implicit conversions (e.g., char -> int, int -> float)
    for (int i = 0; i < params.size(); i++) {
      checker.ensureAssignable(arguments.get(i), params.get(i), node);
    }
    
    // Result type is the function return type
    node.attributes().type(functionType.returnType());
    // Function calls are r-values (cannot be assigned to)
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  /**
   * Handles struct member access: struct.field.
   * 
   * <p>Grammar:
   *   <postfiks_izraz> TOCKA IDN
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Base must be a struct type</li>
   *   <li>Field name must exist in the struct</li>
   *   <li>Result type is the field type</li>
   *   <li>Result is an l-value if base is l-value and field type is not const</li>
   * </ul>
   * 
   * <p>Note: The grammar does not support the -> operator, so pointer dereferencing
   * must be done explicitly: (*ptr).field
   * 
   * @param node the postfix expression node
   * @param base the base expression (struct)
   * @param children all children of the postfix expression node
   */
  private void handleStructMemberAccess(
      NonTerminalNode node, NonTerminalNode base, List<ParseNode> children) {
    Type baseType = base.attributes().type();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    
    // Base must be a struct type
    Type stripped = TypeSystem.stripConst(baseType);
    if (!(stripped instanceof StructType structType)) {
      checker.fail(node);
      return;
    }
    
    // Extract field name from terminal node
    if (children.size() < 3 || !(children.get(2) instanceof TerminalNode fieldToken)) {
      checker.fail(node);
      return;
    }
    
    String fieldName = fieldToken.lexeme();
    // Field must exist in the struct
    if (!structType.hasField(fieldName)) {
      checker.fail(node);
      return;
    }
    
    // Result type is the field type
    Type fieldType = structType.getFieldType(fieldName);
    // Result is an l-value if:
    // 1. Base is an l-value (can be assigned to)
    // 2. Field type is not const (field can be modified)
    boolean isLValue = base.attributes().isLValue() && !TypeSystem.isConst(fieldType);
    
    node.attributes().type(fieldType);
    node.attributes().lValue(isLValue);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void handlePostfixIncDec(NonTerminalNode node, NonTerminalNode base) {
    if (!base.attributes().isLValue() || TypeSystem.isConst(base.attributes().type())) {
      checker.fail(node);
      return;
    }
    Type operandType = base.attributes().type();
    if (!operandType.isScalar()) {
      checker.fail(node);
      return;
    }
    node.attributes().type(operandType);
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
}

