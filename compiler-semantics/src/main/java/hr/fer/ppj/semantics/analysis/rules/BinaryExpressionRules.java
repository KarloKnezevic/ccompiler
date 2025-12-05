package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
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
    
    // Validate that right-hand side type is assignable to left-hand side type
    // This allows implicit conversions (e.g., char -> int, int -> float)
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
    
    // Determine operator type
    String operator = children.get(1) instanceof TerminalNode op ? op.symbol() : "";
    boolean isRelational = operator.equals("OP_LT") || operator.equals("OP_GT") 
        || operator.equals("OP_LTE") || operator.equals("OP_GTE")
        || operator.equals("OP_EQ") || operator.equals("OP_NEQ");
    
    if (isRelational) {
      // Relational operators: <, >, <=, >=, ==, !=
      // Both operands must be scalar
      if (!leftType.isScalar() || !rightType.isScalar()) {
        checker.fail(node);
        return;
      }
      Type leftStripped = TypeSystem.stripConst(leftType);
      Type rightStripped = TypeSystem.stripConst(rightType);
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
      // Arithmetic, bitwise, and logical operators
      // Both operands must be scalar
      if (!leftType.isScalar() || !rightType.isScalar()) {
        checker.fail(node);
        return;
      }
      // Apply C's usual arithmetic conversions
      // Result is int or float depending on operand types
      node.attributes().type(TypeSystem.arithmeticResult(leftType, rightType));
    }
    
    // Binary expressions are always r-values (cannot be assigned to)
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
}

