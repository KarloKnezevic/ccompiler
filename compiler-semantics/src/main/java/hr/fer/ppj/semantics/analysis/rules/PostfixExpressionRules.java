package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PointerType;
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
    
    // Base must be an array type or pointer type
    // Arrays decay to pointers in function parameters, so we need to handle both
    Type stripped = TypeSystem.stripConst(baseType);
    Type elementType = null;
    
    if (stripped instanceof ArrayType arrayType) {
      // True array type
      elementType = arrayType.elementType();
    } else if (stripped instanceof PointerType pointerType) {
      // Pointer type (from array parameter decay or explicit pointer)
      elementType = pointerType.baseType();
    } else {
      checker.fail(node);
      return;
    }
    
    // Result type is the element type
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
    List<NonTerminalNode> argumentExprs = List.of();
    if (children.size() == 4) {
      // Arguments present: <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      arguments = list.attributes().parameterTypes();
      argumentExprs = collectArgumentExpressions(list);
    }
    // Otherwise: <postfiks_izraz> L_ZAGRADA D_ZAGRADA (no arguments)
    else {
      argumentExprs = List.of();
    }
    
    // Validate argument count matches parameter count
    List<Type> params = functionType.parameterTypes();
    if (params.size() != arguments.size()) {
      checker.fail(node);
    }
    // Validate each argument type is assignable to corresponding parameter type
    // This allows implicit conversions (e.g., char -> int, int -> float)
    for (int i = 0; i < params.size(); i++) {
      if (isIntegerToPointerAssignment(arguments.get(i), params.get(i))
          && (i >= argumentExprs.size() || !isNullPointerConstantExpression(argumentExprs.get(i)))) {
        checker.fail(node);
        return;
      }
      checker.ensureAssignable(arguments.get(i), params.get(i), node);
    }
    
    // Result type is the function return type
    node.attributes().type(functionType.returnType());
    // Function calls are r-values (cannot be assigned to)
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private List<NonTerminalNode> collectArgumentExpressions(NonTerminalNode listNode) {
    var children = listNode.children();
    if (children.size() == 1) {
      return List.of(NodeUtils.asNonTerminal(children.get(0)));
    }
    if (children.size() == 3) {
      List<NonTerminalNode> result = new java.util.ArrayList<>(collectArgumentExpressions(
          NodeUtils.asNonTerminal(children.get(0))));
      result.add(NodeUtils.asNonTerminal(children.get(2)));
      return result;
    }
    return List.of();
  }

  private boolean isIntegerToPointerAssignment(Type source, Type target) {
    Type sourceStripped = TypeSystem.stripConst(source);
    Type targetStripped = TypeSystem.stripConst(target);
    return sourceStripped == hr.fer.ppj.semantics.types.PrimitiveType.INT
        && targetStripped instanceof PointerType;
  }

  private boolean isNullPointerConstantExpression(NonTerminalNode expr) {
    Integer value = evaluateIntegerConstant(expr);
    return value != null && value == 0;
  }

  private Integer evaluateIntegerConstant(NonTerminalNode node) {
    var children = node.children();
    String symbol = node.symbol();

    if ("<primarni_izraz>".equals(symbol)) {
      if (children.size() == 1 && children.get(0) instanceof TerminalNode term) {
        if ("BROJ".equals(term.symbol())) {
          try {
            return (int) checker.parseIntegerLiteral(term.lexeme(), node);
          } catch (RuntimeException ex) {
            return null;
          }
        }
        return null;
      }
      if (children.size() == 3 && children.get(1) instanceof NonTerminalNode nested) {
        return evaluateIntegerConstant(nested);
      }
      return null;
    }

    if ("<cast_izraz>".equals(symbol)) {
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return evaluateIntegerConstant(child);
      }
      if (children.size() == 4 && children.get(3) instanceof NonTerminalNode castExpr) {
        return evaluateIntegerConstant(castExpr);
      }
      return null;
    }

    if ("<unarni_izraz>".equals(symbol)) {
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return evaluateIntegerConstant(child);
      }
      if (children.size() == 2 && children.get(0) instanceof TerminalNode opTerm
          && children.get(1) instanceof NonTerminalNode operand) {
        Integer operandValue = evaluateIntegerConstant(operand);
        if (operandValue == null) {
          return null;
        }
        return switch (opTerm.symbol()) {
          case "PLUS" -> operandValue;
          case "MINUS" -> -operandValue;
          default -> null;
        };
      }
      return null;
    }

    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      return evaluateIntegerConstant(child);
    }

    if (children.size() == 3
        && children.get(0) instanceof NonTerminalNode leftNode
        && children.get(1) instanceof TerminalNode op
        && children.get(2) instanceof NonTerminalNode rightNode) {
      Integer left = evaluateIntegerConstant(leftNode);
      Integer right = evaluateIntegerConstant(rightNode);
      if (left == null || right == null) {
        return null;
      }
      return applyIntegerOperator(left, right, op.symbol(), op.lexeme());
    }
    return null;
  }

  private Integer applyIntegerOperator(int left, int right, String opSymbol, String opLexeme) {
    return switch (opLexeme) {
      case "+" -> left + right;
      case "-" -> left - right;
      case "*" -> left * right;
      case "/" -> right == 0 ? null : left / right;
      case "%" -> right == 0 ? null : left % right;
      case "==" -> left == right ? 1 : 0;
      case "!=" -> left != right ? 1 : 0;
      case "<" -> left < right ? 1 : 0;
      case "<=" -> left <= right ? 1 : 0;
      case ">" -> left > right ? 1 : 0;
      case ">=" -> left >= right ? 1 : 0;
      case "&" -> left & right;
      case "|" -> left | right;
      case "^" -> left ^ right;
      case "&&" -> (left != 0 && right != 0) ? 1 : 0;
      case "||" -> (left != 0 || right != 0) ? 1 : 0;
      default -> switch (opSymbol) {
        case "PLUS" -> left + right;
        case "MINUS" -> left - right;
        case "ASTERISK" -> left * right;
        case "OP_DIJELI" -> right == 0 ? null : left / right;
        case "OP_MOD" -> right == 0 ? null : left % right;
        case "OP_EQ" -> left == right ? 1 : 0;
        case "OP_NEQ" -> left != right ? 1 : 0;
        case "OP_LT" -> left < right ? 1 : 0;
        case "OP_LTE" -> left <= right ? 1 : 0;
        case "OP_GT" -> left > right ? 1 : 0;
        case "OP_GTE" -> left >= right ? 1 : 0;
        case "OP_BIN_I" -> left & right;
        case "OP_BIN_ILI" -> left | right;
        case "OP_BIN_XILI" -> left ^ right;
        case "OP_I" -> (left != 0 && right != 0) ? 1 : 0;
        case "OP_ILI" -> (left != 0 || right != 0) ? 1 : 0;
        default -> null;
      };
    };
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
