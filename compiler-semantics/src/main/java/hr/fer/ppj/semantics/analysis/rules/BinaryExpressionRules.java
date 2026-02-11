package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;

/**
 * Semantic rules for binary expressions, assignment, and comma expressions.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Binary operators (arithmetic, logical, relational, bitwise)</li>
 *   <li>Assignment expressions</li>
 *   <li>Comma expressions</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class BinaryExpressionRules {
  
  private final SemanticChecker checker;
  
  BinaryExpressionRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<izraz>", this::visitIzraz);
    checker.registerRule("<izraz_pridruzivanja>", this::visitIzrazPridruzivanja);
    checker.registerRule("<log_ili_izraz>", this::visitBinaryExpression);
    checker.registerRule("<log_i_izraz>", this::visitBinaryExpression);
    checker.registerRule("<bin_ili_izraz>", this::visitBinaryExpression);
    checker.registerRule("<bin_xili_izraz>", this::visitBinaryExpression);
    checker.registerRule("<bin_i_izraz>", this::visitBinaryExpression);
    checker.registerRule("<jednakosni_izraz>", this::visitBinaryExpression);
    checker.registerRule("<odnosni_izraz>", this::visitBinaryExpression);
    checker.registerRule("<aditivni_izraz>", this::visitBinaryExpression);
    checker.registerRule("<multiplikativni_izraz>", this::visitBinaryExpression);
  }
  
  private void visitIzraz(NonTerminalNode node) {
    var children = node.children();
    
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    
    NonTerminalNode left = NodeUtils.asNonTerminal(children.get(0));
    NonTerminalNode right = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(left);
    checker.visitNonTerminal(right);
    
    node.attributes().type(right.attributes().type());
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  /**
   * Performs semantic analysis for assignment expressions.
   * 
   * <p>Grammar:
   *   <izraz_pridruzivanja> ::= <log_ili_izraz>
   *                           | <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Left-hand side must be an l-value (can be assigned to)</li>
   *   <li>Left-hand side must not be const-qualified</li>
   *   <li>Right-hand side type must be assignable to left-hand side type</li>
   *   <li>Result type is the left-hand side type</li>
   *   <li>Result is an r-value (assignment expression itself cannot be assigned to)</li>
   * </ul>
   * 
   * @param node the assignment expression node
   */
  private void visitIzrazPridruzivanja(NonTerminalNode node) {
    var children = node.children();
    
    // Base case: single operand (no assignment)
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    
    // Assignment case: <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
    NonTerminalNode lhs = NodeUtils.asNonTerminal(children.get(0));
    NonTerminalNode rhs = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(lhs);
    checker.visitNonTerminal(rhs);
    
    // Left-hand side must be an l-value (can be assigned to)
    // Left-hand side must not be const-qualified (const values cannot be modified)
    if (!lhs.attributes().isLValue() || TypeSystem.isConst(lhs.attributes().type())) {
      checker.fail(node);
    }
    
    // int -> pointer is only valid for null pointer constants
    if (isIntegerToPointerAssignment(rhs.attributes().type(), lhs.attributes().type())
        && !isNullPointerConstantExpression(rhs)) {
      checker.fail(node);
      return;
    }

    // Validate that right-hand side type is assignable to left-hand side type.
    // This allows implicit conversions (e.g., char -> int, int -> float).
    checker.ensureAssignable(rhs.attributes().type(), lhs.attributes().type(), node);
    
    // Result type is the left-hand side type
    node.attributes().type(lhs.attributes().type());
    // Assignment expression is an r-value (cannot be assigned to)
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  /**
   * Performs semantic analysis for binary expressions.
   * 
   * <p>Handles all binary operators:
   * <ul>
   *   <li>Arithmetic: +, -, *, /, %</li>
   *   <li>Relational: <, >, <=, >=, ==, !=</li>
   *   <li>Bitwise: &, ^, |</li>
   *   <li>Logical: &&, ||</li>
   * </ul>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Both operands must be scalar types</li>
   *   <li>Relational operators: result is int (0 or 1)</li>
   *   <li>Pointer comparisons: base types must be compatible</li>
   *   <li>Arithmetic operators: result type follows C's usual arithmetic conversions</li>
   * </ul>
   * 
   * @param node the binary expression node
   */
  private void visitBinaryExpression(NonTerminalNode node) {
    var children = node.children();
    // Single operand case (base case for recursive grammar)
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    // Binary operator case: <left> <operator> <right>
    NonTerminalNode left = NodeUtils.asNonTerminal(children.get(0));
    NonTerminalNode right = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(left);
    checker.visitNonTerminal(right);
    
    Type leftType = left.attributes().type();
    Type rightType = right.attributes().type();
    Type leftValueType = decayArrayToPointerType(leftType);
    Type rightValueType = decayArrayToPointerType(rightType);
    
    // Determine operator type
    String operator = children.get(1) instanceof TerminalNode op ? op.symbol() : "";
    boolean isRelational = operator.equals("OP_LT") || operator.equals("OP_GT") 
        || operator.equals("OP_LTE") || operator.equals("OP_GTE")
        || operator.equals("OP_EQ") || operator.equals("OP_NEQ");
    
    if (isRelational) {
      // Relational operators: <, >, <=, >=, ==, !=
      // Both operands must be scalar
      if (!leftValueType.isScalar() || !rightValueType.isScalar()) {
        checker.fail(node);
        return;
      }
      Type leftStripped = TypeSystem.stripConst(leftValueType);
      Type rightStripped = TypeSystem.stripConst(rightValueType);
      // Pointer comparisons: base types must be compatible
      if (leftStripped instanceof PointerType && rightStripped instanceof PointerType) {
        PointerType leftPtr = (PointerType) leftStripped;
        PointerType rightPtr = (PointerType) rightStripped;
        if (!TypeSystem.equalsIgnoringConst(leftPtr.baseType(), rightPtr.baseType())) {
          checker.fail(node);
          return;
        }
      }
      // Relational operators yield int (0 for false, 1 for true)
      node.attributes().type(PrimitiveType.INT);
    } else {
      if ("<aditivni_izraz>".equals(node.symbol())) {
        Type additiveType = resolveAdditiveExpressionType(leftValueType, rightValueType, operator);
        if (additiveType == null) {
          checker.fail(node);
          return;
        }
        node.attributes().type(additiveType);
      } else {
        // Arithmetic, bitwise, and logical operators
        // Both operands must be scalar
        if (!leftValueType.isScalar() || !rightValueType.isScalar()) {
          checker.fail(node);
          return;
        }
        // Apply C's usual arithmetic conversions
        // Result is int or float depending on operand types
        node.attributes().type(TypeSystem.arithmeticResult(leftValueType, rightValueType));
      }
    }
    
    // Binary expressions are always r-values (cannot be assigned to)
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private Type resolveAdditiveExpressionType(Type leftType, Type rightType, String operator) {
    Type leftStripped = TypeSystem.stripConst(leftType);
    Type rightStripped = TypeSystem.stripConst(rightType);

    if (leftStripped instanceof PointerType leftPointer
        && rightStripped instanceof PointerType rightPointer) {
      if ("MINUS".equals(operator)
          && TypeSystem.equalsIgnoringConst(leftPointer.baseType(), rightPointer.baseType())) {
        return PrimitiveType.INT;
      }
      return null;
    }

    if (leftStripped instanceof PointerType) {
      return isPointerOffsetType(rightStripped) ? leftType : null;
    }

    if (rightStripped instanceof PointerType) {
      if (!"PLUS".equals(operator)) {
        return null;
      }
      return isPointerOffsetType(leftStripped) ? rightType : null;
    }

    if (!leftStripped.isScalar() || !rightStripped.isScalar()) {
      return null;
    }

    return TypeSystem.arithmeticResult(leftStripped, rightStripped);
  }

  private Type decayArrayToPointerType(Type type) {
    Type stripped = TypeSystem.stripConst(type);
    if (stripped instanceof ArrayType arrayType) {
      return new PointerType(arrayType.elementType(), false);
    }
    return type;
  }

  private boolean isPointerOffsetType(Type type) {
    Type stripped = TypeSystem.stripConst(type);
    return stripped == PrimitiveType.INT || stripped == PrimitiveType.CHAR;
  }

  private boolean isIntegerToPointerAssignment(Type source, Type target) {
    Type sourceStripped = TypeSystem.stripConst(source);
    Type targetStripped = TypeSystem.stripConst(target);
    return sourceStripped == PrimitiveType.INT && targetStripped instanceof PointerType;
  }

  /**
   * Null pointer constant in C is an integer constant expression with value zero.
   */
  private boolean isNullPointerConstantExpression(NonTerminalNode expr) {
    Integer value = evaluateIntegerConstant(expr);
    return value != null && value == 0;
  }

  /**
   * Best-effort evaluator for integer constant expressions used by pointer checks.
   * Returns null when expression is not an integer constant expression.
   */
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
        if ("ZNAK".equals(term.symbol()) && term.lexeme().length() >= 3) {
          return (int) term.lexeme().charAt(1);
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
      if (children.size() == 2 && children.get(0) instanceof NonTerminalNode unaryOp
          && children.get(1) instanceof NonTerminalNode operand) {
        if (unaryOp.children().isEmpty() || !(unaryOp.children().get(0) instanceof TerminalNode opTerm)) {
          return null;
        }
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

    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt) {
        Integer nested = evaluateIntegerConstant(nt);
        if (nested != null) {
          return nested;
        }
      }
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
}
