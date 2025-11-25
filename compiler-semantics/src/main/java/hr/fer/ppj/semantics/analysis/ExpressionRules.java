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
 * Semantic handlers for all expression-related productions.
 */
final class ExpressionRules {

  private final SemanticChecker checker;

  ExpressionRules(SemanticChecker checker) {
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
    checker.registerRule("<cast_izraz>", this::visitCastIzraz);
    checker.registerRule("<unarni_izraz>", this::visitUnarniIzraz);
    checker.registerRule("<postfiks_izraz>", this::visitPostfiksIzraz);
    checker.registerRule("<primarni_izraz>", this::visitPrimarniIzraz);
    checker.registerRule("<lista_argumenata>", this::visitListaArgumenata);
  }

  private void visitIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    NonTerminalNode right = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(0)));
    checker.visitNonTerminal(right);
    node.attributes().type(right.attributes().type());
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private void visitIzrazPridruzivanja(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    NonTerminalNode lhs = NodeUtils.asNonTerminal(children.get(0));
    NonTerminalNode rhs = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(lhs);
    checker.visitNonTerminal(rhs);
    if (!lhs.attributes().isLValue() || TypeSystem.isConst(lhs.attributes().type())) {
      checker.fail(node);
    }
    checker.ensureAssignable(rhs.attributes().type(), lhs.attributes().type(), node);
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

  private void visitUnarniIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode child = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(child);
      checker.copyExpressionAttributes(node, child);
      return;
    }
    if (children.get(0) instanceof TerminalNode operator
        && ("OP_INC".equals(operator.symbol()) || "OP_DEC".equals(operator.symbol()))) {
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
    TerminalNode operator = (TerminalNode) NodeUtils.asNonTerminal(children.get(0)).children().get(0);
    if (!switch (operator.symbol()) {
          case "PLUS", "MINUS", "OP_TILDA", "OP_NEG" -> true;
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

  private void visitPostfiksIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
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
      case "OP_INC", "OP_DEC" -> {
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

  private void handleArrayElement(
      NonTerminalNode node, NonTerminalNode base, List<ParseNode> children) {
    Type baseType = base.attributes().type();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    NonTerminalNode index = NodeUtils.asNonTerminal(children.get(2));
    checker.visitNonTerminal(index);
    checker.ensureIntConvertible(index.attributes().type(), node);
    Type stripped = TypeSystem.stripConst(baseType);
    if (!(stripped instanceof ArrayType arrayType)) {
      checker.fail(node);
      return;
    }
    Type elementType = arrayType.elementType();
    node.attributes().type(elementType);
    node.attributes().lValue(!TypeSystem.isConst(elementType));
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private void handleFunctionCall(
      NonTerminalNode node, NonTerminalNode base, List<ParseNode> children) {
    Type type = base.attributes().type();
    Type stripped = TypeSystem.stripConst(type);
    if (!(stripped instanceof FunctionType functionType)) {
      checker.fail(node);
      return;
    }
    List<Type> arguments = List.of();
    if (children.size() == 4) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      arguments = list.attributes().parameterTypes();
    }
    List<Type> params = functionType.parameterTypes();
    if (params.size() != arguments.size()) {
      checker.fail(node);
    }
    for (int i = 0; i < params.size(); i++) {
      checker.ensureAssignable(arguments.get(i), params.get(i), node);
    }
    node.attributes().type(functionType.returnType());
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }

  private void visitPrimarniIzraz(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 3 && children.get(0) instanceof TerminalNode lParen) {
      if (!"L_ZAGRADA".equals(lParen.symbol())
          || !(children.get(2) instanceof TerminalNode rParen)
          || !"D_ZAGRADA".equals(rParen.symbol())) {
        checker.fail(node);
      }
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(1));
      checker.visitNonTerminal(expr);
      checker.copyExpressionAttributes(node, expr);
      return;
    }
    ParseNode child = children.get(0);
    if (child instanceof TerminalNode terminal) {
      switch (terminal.symbol()) {
        case "IDN" -> handleIdentifier(node, terminal);
        case "BROJ" -> {
          checker.parseIntegerLiteral(terminal.lexeme(), node);
          node.attributes().type(PrimitiveType.INT);
          node.attributes().lValue(false);
          node.attributes().stringLiteral(false);
          node.attributes().stringLiteralLength(0);
        }
        case "ZNAK" -> {
          checker.parseCharacterLiteral(terminal.lexeme(), node);
          node.attributes().type(PrimitiveType.CHAR);
          node.attributes().lValue(false);
          node.attributes().stringLiteral(false);
          node.attributes().stringLiteralLength(0);
        }
        case "NIZ_ZNAKOVA" -> handleStringLiteral(node, terminal);
        default -> checker.fail(node);
      }
      return;
    }
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

  private void visitListaArgumenata(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(expr);
      node.attributes().parameterTypes(List.of(expr.attributes().type()));
      return;
    }
    if (children.size() == 3) {
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
}


