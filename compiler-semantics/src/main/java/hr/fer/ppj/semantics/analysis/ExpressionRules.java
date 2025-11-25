package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantic rule implementations for all expression-related productions defined in
 * {@code config/semantics_definition.txt}.
 * 
 * <p>This class implements the semantic analysis for the expression hierarchy in PPJ-C,
 * including:
 * <ul>
 *   <li>Primary expressions (identifiers, literals, parenthesized expressions)</li>
 *   <li>Postfix expressions (array indexing, function calls, increment/decrement)</li>
 *   <li>Unary expressions (prefix increment/decrement, unary operators)</li>
 *   <li>Cast expressions (explicit type conversions)</li>
 *   <li>Binary expressions (arithmetic, relational, logical, bitwise operators)</li>
 *   <li>Assignment expressions</li>
 *   <li>Comma expressions</li>
 * </ul>
 * 
 * <p>Each method in this class corresponds directly to one or more productions in the
 * semantic definition and implements the type checking, l-value analysis, and attribute
 * propagation rules specified there.
 * 
 * @see SemanticChecker for the main semantic analysis coordinator
 * @see DeclarationRules for declaration-related semantic rules
 * @see StatementRules for statement-related semantic rules
 */
final class ExpressionRules {

  private final SemanticChecker checker;

  /**
   * Constructs expression rules and registers all expression-related semantic handlers.
   * 
   * <p>This constructor registers semantic rule handlers for all expression non-terminals
   * defined in {@code semantics_definition.txt}. The handlers implement type checking,
   * l-value analysis, and semantic attribute propagation according to the PPJ-C specification.
   * 
   * @param checker the semantic checker that coordinates the analysis
   */
  ExpressionRules(SemanticChecker checker) {
    this.checker = checker;
    
    // Register handlers for all expression-related productions
    checker.registerRule("<izraz>", this::visitIzraz);
    checker.registerRule("<izraz_pridruzivanja>", this::visitIzrazPridruzivanja);
    
    // Binary expression handlers (all use the same logic with int-convertible operands)
    checker.registerRule("<log_ili_izraz>", this::visitBinaryExpression);
    checker.registerRule("<log_i_izraz>", this::visitBinaryExpression);
    checker.registerRule("<bin_ili_izraz>", this::visitBinaryExpression);
    checker.registerRule("<bin_xili_izraz>", this::visitBinaryExpression);
    checker.registerRule("<bin_i_izraz>", this::visitBinaryExpression);
    checker.registerRule("<jednakosni_izraz>", this::visitBinaryExpression);
    checker.registerRule("<odnosni_izraz>", this::visitBinaryExpression);
    checker.registerRule("<aditivni_izraz>", this::visitBinaryExpression);
    checker.registerRule("<multiplikativni_izraz>", this::visitBinaryExpression);
    
    // Specialized expression handlers
    checker.registerRule("<cast_izraz>", this::visitCastIzraz);
    checker.registerRule("<unarni_izraz>", this::visitUnarniIzraz);
    checker.registerRule("<postfiks_izraz>", this::visitPostfiksIzraz);
    checker.registerRule("<primarni_izraz>", this::visitPrimarniIzraz);
    checker.registerRule("<lista_argumenata>", this::visitListaArgumenata);
    checker.registerRule("<unarni_operator>", this::visitUnarniOperator);
  }

  /**
   * Implements semantic rules for {@code <izraz>} (comma expressions).
   * 
   * <p>From {@code semantics_definition.txt}:
   * <pre>
   * &lt;izraz&gt; ::= &lt;izraz_pridruzivanja&gt;
   * &lt;izraz&gt; ::= &lt;izraz&gt; ZAREZ &lt;izraz_pridruzivanja&gt;
   * </pre>
   * 
   * <p>Semantic rules:
   * <ul>
   *   <li>Single expression: inherits all attributes from the assignment expression</li>
   *   <li>Comma expression: evaluates both operands, result has type and value of the right operand,
   *       result is not an l-value</li>
   * </ul>
   * 
   * @param node the {@code <izraz>} node to analyze
   */
  private void visitIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    if (children.size() == 1) {
      // <izraz> ::= <izraz_pridruzivanja>
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    
    // <izraz> ::= <izraz> ZAREZ <izraz_pridruzivanja>
    NonTerminalNode left = NodeUtils.asNonTerminal(children.get(0));
    NonTerminalNode right = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(left);
    checker.visitNonTerminal(right);
    
    // Result has type of right operand, is not an l-value
    node.attributes().type(right.attributes().type());
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  /**
   * Implements semantic rules for {@code <izraz_pridruzivanja>} (assignment expressions).
   * 
   * <p>From {@code semantics_definition.txt}:
   * <pre>
   * &lt;izraz_pridruzivanja&gt; ::= &lt;log_ili_izraz&gt;
   * &lt;izraz_pridruzivanja&gt; ::= &lt;postfiks_izraz&gt; OP_PRIDRUZI &lt;izraz_pridruzivanja&gt;
   * </pre>
   * 
   * <p>Semantic rules:
   * <ul>
   *   <li>Single expression: inherits all attributes from the logical OR expression</li>
   *   <li>Assignment: left operand must be an l-value and not const-qualified,
   *       right operand must be assignable to left operand's type,
   *       result has type of left operand and is not an l-value</li>
   * </ul>
   * 
   * @param node the {@code <izraz_pridruzivanja>} node to analyze
   */
  private void visitIzrazPridruzivanja(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    if (children.size() == 1) {
      // <izraz_pridruzivanja> ::= <log_ili_izraz>
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    
    // <izraz_pridruzivanja> ::= <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
    NonTerminalNode lhs = NodeUtils.asNonTerminal(children.get(0));
    NonTerminalNode rhs = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(lhs);
    checker.visitNonTerminal(rhs);
    
    // Left operand must be a modifiable l-value (not const)
    if (!lhs.attributes().isLValue() || TypeSystem.isConst(lhs.attributes().type())) {
      checker.fail(node);
    }
    
    // Right operand must be assignable to left operand
    checker.ensureAssignable(rhs.attributes().type(), lhs.attributes().type(), node);
    
    // Result has type of left operand, is not an l-value
    node.attributes().type(lhs.attributes().type());
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private void visitBinaryExpression(NonTerminalNode node) {
    List<ParseNode> children = node.children();
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
    checker.ensureIntConvertible(left.attributes().type(), node);
    checker.ensureIntConvertible(right.attributes().type(), node);
    node.attributes().type(PrimitiveType.INT);
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private void visitCastIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    NonTerminalNode type = NodeUtils.asNonTerminal(children.get(1));
    NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(3));
    checker.visitNonTerminal(type);
    checker.visitNonTerminal(expr);
    Type target = type.attributes().type();
    if (target == null || !checkerCanCast(expr.attributes().type(), target, node)) {
      checker.fail(node);
    }
    node.attributes().type(target);
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private boolean checkerCanCast(Type source, Type target, NonTerminalNode ctx) {
    if (source == null || target == null) {
      return false;
    }
    return TypeSystem.canCast(source, target);
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;unarni_izraz&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;unarni_izraz&gt; ::= &lt;postfiks_izraz&gt;
   * &lt;unarni_izraz&gt; ::= OP_INC &lt;unarni_izraz&gt;
   * &lt;unarni_izraz&gt; ::= OP_DEC &lt;unarni_izraz&gt;
   * &lt;unarni_izraz&gt; ::= &lt;unarni_operator&gt; &lt;cast_izraz&gt;
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>For &lt;postfiks_izraz&gt;: inherit all attributes</li>
   *   <li>For OP_INC/OP_DEC: operand must be l-value and int-convertible, result type is int</li>
   *   <li>For unary operators: operand must be int-convertible, result type is int</li>
   * </ul>
   *
   * @param node the &lt;unarni_izraz&gt; node to analyze
   */
  private void visitUnarniIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      // Rule: <unarni_izraz> ::= <postfiks_izraz>
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    if (children.get(0) instanceof TerminalNode operator
        && (SemanticConstants.OP_INC.equals(operator.symbol()) || SemanticConstants.OP_DEC.equals(operator.symbol()))) {
      // Rule: OP_INC/OP_DEC <unarni_izraz> - operand must be l-value and int-convertible
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(1));
      checker.visitNonTerminal(child);
      if (!child.attributes().isLValue()) {
        checker.fail(node);
      }
      checker.ensureIntConvertible(child.attributes().type(), node);
      node.attributes().type(PrimitiveType.INT);
      node.attributes().lValue(false);
      node.attributes().stringLiteral(false);
      node.attributes().stringLiteralLength(0);
      return;
    }
    // Rule: <unarni_operator> <cast_izraz> - operand must be int-convertible
    TerminalNode operator = (TerminalNode) NodeUtils.asNonTerminal(children.get(0)).children().get(0);
    if (!switch (operator.symbol()) {
          case SemanticConstants.PLUS, SemanticConstants.MINUS, SemanticConstants.OP_TILDA, SemanticConstants.OP_NEG -> true;
          default -> false;
        }) {
      checker.fail(node);
    }
    NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(1));
    checker.visitNonTerminal(expr);
    checker.ensureIntConvertible(expr.attributes().type(), node);
    node.attributes().type(PrimitiveType.INT);
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  /**
   * Implements semantic rules for {@code <postfiks_izraz>} (postfix expressions).
   * 
   * <p>From {@code semantics_definition.txt}:
   * <pre>
   * &lt;postfiks_izraz&gt; ::= &lt;primarni_izraz&gt;
   *                     | &lt;postfiks_izraz&gt; L_UGL_ZAGRADA &lt;izraz&gt; D_UGL_ZAGRADA
   *                     | &lt;postfiks_izraz&gt; L_ZAGRADA D_ZAGRADA
   *                     | &lt;postfiks_izraz&gt; L_ZAGRADA &lt;lista_argumenata&gt; D_ZAGRADA
   *                     | &lt;postfiks_izraz&gt; OP_INC
   *                     | &lt;postfiks_izraz&gt; OP_DEC
   * </pre>
   * 
   * <p>Semantic rules:
   * <ul>
   *   <li><strong>Primary expression:</strong> Inherits all attributes</li>
   *   <li><strong>Array indexing:</strong> Base must be array type, index must be int-convertible,
   *       result type is element type, l-value if element type is not const</li>
   *   <li><strong>Function call:</strong> Base must be function type, argument types must match
   *       parameter types, result type is return type, not an l-value</li>
   *   <li><strong>Postfix increment/decrement:</strong> Operand must be int-convertible l-value,
   *       result type is int, not an l-value</li>
   * </ul>
   * 
   * @param node the {@code <postfiks_izraz>} node to analyze
   */
  private void visitPostfiksIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    if (children.size() == 1) {
      // <postfiks_izraz> ::= <primarni_izraz>
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    
    // Handle postfix operations
    NonTerminalNode base = NodeUtils.asNonTerminal(children.get(0));
    checker.visitNonTerminal(base);
    TerminalNode op = (TerminalNode) children.get(1);
    
    switch (op.symbol()) {
      case "L_UGL_ZAGRADA" -> handleArrayElement(node, base, children);
      case "L_ZAGRADA" -> handleFunctionCall(node, base, children);
      case SemanticConstants.OP_INC, SemanticConstants.OP_DEC -> {
        // Postfix increment/decrement: operand must be int-convertible l-value
        if (!base.attributes().isLValue()) {
          checker.fail(node);
        }
        checker.ensureIntConvertible(base.attributes().type(), node);
        node.attributes().type(PrimitiveType.INT);
        node.attributes().lValue(false);
        node.attributes().stringLiteral(false);
        node.attributes().stringLiteralLength(0);
      }
      default -> checker.fail(node);
    }
  }

  /**
   * Handles array indexing semantic rules for {@code <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA}.
   * 
   * <p>Semantic rules from {@code semantics_definition.txt}:
   * <ul>
   *   <li>Base expression must have array type {@code array(T)}</li>
   *   <li>Index expression must be int-convertible</li>
   *   <li>Result type is the element type {@code T}</li>
   *   <li>Result is an l-value if element type is not const-qualified</li>
   * </ul>
   * 
   * @param node the postfix expression node to set attributes on
   * @param base the base expression (must be array type)
   * @param children all children of the postfix expression for accessing the index
   */
  private void handleArrayElement(
      NonTerminalNode node, NonTerminalNode base, List<ParseNode> children) {
    Type baseType = base.attributes().type();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    
    // Analyze index expression (must be int-convertible)
    NonTerminalNode index = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(index);
    checker.ensureIntConvertible(index.attributes().type(), node);
    
    // Base must be array type
    Type stripped = TypeSystem.stripConst(baseType);
    if (!(stripped instanceof ArrayType arrayType)) {
      checker.fail(node);
      return;
    }
    
    // Result has element type, is l-value if element type is not const
    Type elementType = arrayType.elementType();
    node.attributes().type(elementType);
    node.attributes().lValue(!TypeSystem.isConst(elementType));
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  /**
   * Handles function call semantic rules for {@code <postfiks_izraz> L_ZAGRADA [<lista_argumenata>] D_ZAGRADA}.
   * 
   * <p>Semantic rules from {@code semantics_definition.txt}:
   * <ul>
   *   <li>Base expression must have function type {@code function(T1, T2, ..., Tn) -> R}</li>
   *   <li>Number of arguments must match number of parameters</li>
   *   <li>Each argument type must be assignable to corresponding parameter type</li>
   *   <li>Result type is the function's return type {@code R}</li>
   *   <li>Result is not an l-value</li>
   * </ul>
   * 
   * @param node the postfix expression node to set attributes on
   * @param base the base expression (must be function type)
   * @param children all children of the postfix expression for accessing arguments
   */
  private void handleFunctionCall(
      NonTerminalNode node, NonTerminalNode base, List<ParseNode> children) {
    Type type = base.attributes().type();
    Type stripped = TypeSystem.stripConst(type);
    if (!(stripped instanceof FunctionType functionType)) {
      checker.fail(node);
      return;
    }
    
    // Extract argument types (empty list if no arguments)
    List<Type> arguments = List.of();
    if (children.size() == 4) {
      // Function call with arguments: f(arg1, arg2, ...)
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      arguments = list.attributes().parameterTypes();
    }
    // else: Function call without arguments: f()
    
    // Check argument count and types
    List<Type> params = functionType.parameterTypes();
    if (params.size() != arguments.size()) {
      checker.fail(node);
    }
    for (int i = 0; i < params.size(); i++) {
      checker.ensureAssignable(arguments.get(i), params.get(i), node);
    }
    
    // Result has function's return type, is not an l-value
    node.attributes().type(functionType.returnType());
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  /**
   * Implements semantic rules for {@code <primarni_izraz>} (primary expressions).
   * 
   * <p>From {@code semantics_definition.txt}:
   * <pre>
   * &lt;primarni_izraz&gt; ::= IDN
   *                    | BROJ
   *                    | ZNAK
   *                    | NIZ_ZNAKOVA
   *                    | L_ZAGRADA &lt;izraz&gt; D_ZAGRADA
   * </pre>
   * 
   * <p>Semantic rules:
   * <ul>
   *   <li><strong>IDN:</strong> Identifier must be declared in current scope,
   *       type and l-value status depend on symbol type (variable vs function)</li>
   *   <li><strong>BROJ:</strong> Integer literal, type is {@code int}, not an l-value</li>
   *   <li><strong>ZNAK:</strong> Character literal, type is {@code char}, not an l-value</li>
   *   <li><strong>NIZ_ZNAKOVA:</strong> String literal, type is {@code const char[]}, not an l-value</li>
   *   <li><strong>Parenthesized:</strong> Inherits all attributes from the inner expression</li>
   * </ul>
   * 
   * @param node the {@code <primarni_izraz>} node to analyze
   */
  private void visitPrimarniIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    // Handle parenthesized expressions: L_ZAGRADA <izraz> D_ZAGRADA
    if (children.size() == 3 && children.get(0) instanceof TerminalNode lParen) {
      if (!SemanticConstants.L_ZAGRADA.equals(lParen.symbol())
          || !(children.get(2) instanceof TerminalNode rParen)
          || !SemanticConstants.D_ZAGRADA.equals(rParen.symbol())) {
        checker.fail(node);
      }
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(1));
      checker.visitNonTerminal(expr);
      checker.copyExpressionAttributes(node, expr);
      return;
    }
    
    // Handle terminal primary expressions
    ParseNode child = children.get(0);
    if (child instanceof TerminalNode terminal) {
      switch (terminal.symbol()) {
        case SemanticConstants.IDN -> handleIdentifier(node, terminal);
        case SemanticConstants.BROJ -> {
          // Integer literal: validate format and set type to int
          checker.parseIntegerLiteral(terminal.lexeme(), node);
          node.attributes().type(PrimitiveType.INT);
          node.attributes().lValue(false);
          node.attributes().stringLiteral(false);
          node.attributes().stringLiteralLength(0);
        }
        case SemanticConstants.ZNAK -> {
          // Character literal: validate format and set type to char
          checker.parseCharacterLiteral(terminal.lexeme(), node);
          node.attributes().type(PrimitiveType.CHAR);
          node.attributes().lValue(false);
          node.attributes().stringLiteral(false);
          node.attributes().stringLiteralLength(0);
        }
        case SemanticConstants.NIZ_ZNAKOVA -> handleStringLiteral(node, terminal);
        default -> checker.fail(node);
      }
      return;
    }
    
    // Handle nested non-terminal (should not occur in normal parsing)
    NonTerminalNode nested = NodeUtils.asNonTerminal(child);
    checker.visitNonTerminal(nested);
    checker.copyExpressionAttributes(node, nested);
  }

  private void handleIdentifier(NonTerminalNode node, TerminalNode id) {
    Symbol symbol = checker.currentScope().lookup(id.lexeme()).orElse(null);
    if (symbol instanceof VariableSymbol variableSymbol) {
      node.attributes().type(variableSymbol.type());
      boolean lValue =
          !(TypeSystem.stripConst(variableSymbol.type()) instanceof ArrayType)
              && !(variableSymbol.type() instanceof FunctionType);
      node.attributes().lValue(lValue);
    } else if (symbol instanceof FunctionSymbol functionSymbol) {
      node.attributes().type(functionSymbol.type());
      node.attributes().lValue(false);
    } else {
      checker.fail(node);
    }
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private void handleStringLiteral(NonTerminalNode node, TerminalNode literal) {
    int length = checker.computeStringLiteralLength(literal.lexeme(), node);
    node.attributes().type(new ArrayType(new ConstType(PrimitiveType.CHAR)));
    node.attributes().lValue(false);
    node.attributes().stringLiteral(true);
    node.attributes().stringLiteralLength(length);
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;lista_argumenata&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;lista_argumenata&gt; ::= &lt;izraz_pridruzivanja&gt;
   * &lt;lista_argumenata&gt; ::= &lt;lista_argumenata&gt; ZAREZ &lt;izraz_pridruzivanja&gt;
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>For single argument: provjeri(&lt;izraz_pridruzivanja&gt;), collect type</li>
   *   <li>For multiple arguments: provjeri all expressions, collect types in order</li>
   * </ul>
   *
   * @param node the &lt;lista_argumenata&gt; node to analyze
   */
  private void visitListaArgumenata(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      // Rule: <lista_argumenata> ::= <izraz_pridruzivanja>
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(expr);
      node.attributes().parameterTypes(List.of(expr.attributes().type()));
      return;
    }
    if (children.size() == 3) {
      // Rule: <lista_argumenata> ::= <lista_argumenata> ZAREZ <izraz_pridruzivanja>
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      checker.visitNonTerminal(expr);
      List<Type> result = new ArrayList<>(list.attributes().parameterTypes());
      result.add(expr.attributes().type());
      node.attributes().parameterTypes(result);
      return;
    }
    checker.fail(node);
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;unarni_operator&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;unarni_operator&gt; ::= PLUS
   * &lt;unarni_operator&gt; ::= MINUS
   * &lt;unarni_operator&gt; ::= OP_TILDA
   * &lt;unarni_operator&gt; ::= OP_NEG
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>No specific semantic actions - this is a terminal selection rule</li>
   * </ul>
   *
   * @param node the &lt;unarni_operator&gt; node to analyze
   */
  private void visitUnarniOperator(NonTerminalNode node) {
    // No semantic actions required - just validates the operator token
    // The actual semantic validation is done in the parent <unarni_izraz> rule
  }
}


