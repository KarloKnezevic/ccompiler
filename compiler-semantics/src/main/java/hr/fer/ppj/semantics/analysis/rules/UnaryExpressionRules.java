package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.CastCategoryUtil;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;

/**
 * Semantic rules for unary expressions and casts.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Prefix increment/decrement</li>
 *   <li>Unary operators (+, -, ~, !)</li>
 *   <li>Address-of operator (&)</li>
 *   <li>Dereference operator (*)</li>
 *   <li>Cast expressions</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class UnaryExpressionRules {
  
  private final SemanticChecker checker;
  
  UnaryExpressionRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<unarni_izraz>", this::visitUnarniIzraz);
    checker.registerRule("<cast_izraz>", this::visitCastIzraz);
    checker.registerRule("<unarni_operator>", this::visitUnarniOperator);
  }
  
  private void visitUnarniIzraz(NonTerminalNode node) {
    var children = node.children();
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    
    ParseNode first = children.get(0);
    if (first instanceof TerminalNode operator) {
      if (SemanticConstants.OP_INC.equals(operator.symbol()) 
          || SemanticConstants.OP_DEC.equals(operator.symbol())) {
        handlePrefixIncDec(node, children);
        return;
      }
      
      if (SemanticConstants.AMPERSAND.equals(operator.symbol())) {
        handleAddressOf(node, children);
        return;
      }
      
      if (SemanticConstants.ASTERISK.equals(operator.symbol())) {
        handleDereference(node, children);
        return;
      }
    }
    
    if (first instanceof NonTerminalNode unaryOp) {
      TerminalNode operator = (TerminalNode) unaryOp.children().get(0);
      String opSymbol = operator.symbol();
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(1));
      checker.visitNonTerminal(expr);
      Type operandType = expr.attributes().type();
      
      if (SemanticConstants.AMPERSAND.equals(opSymbol)) {
        handleAddressOf(node, expr);
        return;
      }
      
      if (SemanticConstants.ASTERISK.equals(opSymbol)) {
        handleDereference(node, expr);
        return;
      }
      
      handleUnaryOperator(node, expr, operandType, opSymbol);
      return;
    }
    
    checker.fail(node);
  }
  
  private void handlePrefixIncDec(NonTerminalNode node, List<ParseNode> children) {
    NonTerminalNode child = NodeUtils.asNonTerminal(children.get(1));
    checker.visitNonTerminal(child);
    if (!child.attributes().isLValue() || TypeSystem.isConst(child.attributes().type())) {
      checker.fail(node);
      return;
    }
    Type operandType = child.attributes().type();
    if (!operandType.isScalar()) {
      checker.fail(node);
      return;
    }
    node.attributes().type(operandType);
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  /**
   * Handles address-of operator: &expr.
   * 
   * <p>Grammar:
   *   AMPERSAND <cast_izraz>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Operand must be an l-value (can take address of)</li>
   *   <li>Operand cannot be a function type (functions are not l-values)</li>
   *   <li>Result type is pointer to operand type</li>
   *   <li>Result is not an l-value (address-of yields r-value)</li>
   * </ul>
   * 
   * @param node the unary expression node
   * @param children all children of the unary expression node
   */
  private void handleAddressOf(NonTerminalNode node, List<ParseNode> children) {
    NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(1));
    checker.visitNonTerminal(expr);
    // Operand must be an l-value (can only take address of variables, array elements, etc.)
    if (!expr.attributes().isLValue()) {
      checker.fail(node);
      return;
    }
    Type operandType = expr.attributes().type();
    Type stripped = TypeSystem.stripConst(operandType);
    // Cannot take address of functions (functions are not l-values)
    if (stripped instanceof FunctionType) {
      checker.fail(node);
      return;
    }
    // Result type is pointer to operand type (pointer itself is not const)
    node.attributes().type(new PointerType(operandType, false));
    // Address-of yields an r-value (cannot be assigned to)
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void handleAddressOf(NonTerminalNode node, NonTerminalNode expr) {
    if (!expr.attributes().isLValue()) {
      checker.fail(node);
      return;
    }
    Type operandType = expr.attributes().type();
    Type stripped = TypeSystem.stripConst(operandType);
    if (stripped instanceof FunctionType) {
      checker.fail(node);
      return;
    }
    node.attributes().type(new PointerType(operandType, false));
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  /**
   * Handles dereference operator: *expr.
   * 
   * <p>Grammar:
   *   ASTERISK <cast_izraz>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Operand must be a pointer type</li>
   *   <li>Result type is the pointed-to type</li>
   *   <li>Result is an l-value if pointed-to type is not void or function</li>
   * </ul>
   * 
   * @param node the unary expression node
   * @param children all children of the unary expression node
   */
  private void handleDereference(NonTerminalNode node, List<ParseNode> children) {
    NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(1));
    checker.visitNonTerminal(expr);
    Type operandType = expr.attributes().type();
    Type stripped = TypeSystem.stripConst(operandType);
    // Operand must be a pointer type
    if (!(stripped instanceof PointerType pointerType)) {
      checker.fail(node);
      return;
    }
    // Result type is the pointed-to type
    Type baseType = pointerType.baseType();
    // Result is an l-value if pointed-to type is not void or function
    // (void* and function pointers cannot be dereferenced to l-values)
    boolean isLValue = !baseType.isVoid() && !(baseType instanceof FunctionType);
    node.attributes().type(baseType);
    node.attributes().lValue(isLValue);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void handleDereference(NonTerminalNode node, NonTerminalNode expr) {
    Type operandType = expr.attributes().type();
    Type stripped = TypeSystem.stripConst(operandType);
    if (!(stripped instanceof PointerType pointerType)) {
      checker.fail(node);
      return;
    }
    Type baseType = pointerType.baseType();
    boolean isLValue = !baseType.isVoid() && !(baseType instanceof FunctionType);
    node.attributes().type(baseType);
    node.attributes().lValue(isLValue);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void handleUnaryOperator(NonTerminalNode node, NonTerminalNode expr, Type operandType, String opSymbol) {
    if (!switch (opSymbol) {
          case SemanticConstants.PLUS, SemanticConstants.MINUS, 
               SemanticConstants.OP_TILDA, SemanticConstants.OP_NEG -> true;
          default -> false;
        }) {
      checker.fail(node);
      return;
    }
    if (!operandType.isScalar()) {
      checker.fail(node);
      return;
    }
    // For unary operators, preserve the operand type (especially for FLOAT)
    // Pointer types remain pointers, but scalar types (int, char, float) preserve their type
    Type resultType = operandType instanceof PointerType ? operandType : operandType;
    node.attributes().type(resultType);
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void visitCastIzraz(NonTerminalNode node) {
    var children = node.children();
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    // Explicit cast: L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
    NonTerminalNode type = NodeUtils.asNonTerminal(children.get(1));
    NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(3));
    checker.visitNonTerminal(type);
    checker.visitNonTerminal(expr);
    Type source = expr.attributes().type();
    Type target = type.attributes().type();
    if (target == null || source == null || !TypeSystem.canCast(source, target)) {
      checker.fail(node);
      return;
    }
    
    // Store cast information for IR generation
    node.attributes().type(target);
    node.attributes().castSourceType(source);
    node.attributes().castCategory(
        CastCategoryUtil.determineCastCategory(source, target));
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void visitUnarniOperator(NonTerminalNode node) {
    // No semantic actions required - just validates the operator token
  }
}

