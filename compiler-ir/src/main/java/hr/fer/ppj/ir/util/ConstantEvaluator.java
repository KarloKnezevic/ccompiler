package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates compile-time constants from expression nodes.
 *
 * <p>This utility extracts constant values from simple expressions for use
 * in global initializers. Only supports literals and simple constant expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ConstantEvaluator {

  private ConstantEvaluator() {}

  /**
   * Extracts a constant value from an expression node.
   *
   * <p>This method recursively extracts constants from simple expressions.
   * Only supports literals and simple constant expressions.
   *
   * @param expr the expression node
   * @param targetType the expected type of the constant
   * @return the constant value
   * @throws UnsupportedOperationException if the expression is not a compile-time constant
   */
  public static IrConst extractConstantFromExpression(NonTerminalNode expr, Type targetType) {
    Objects.requireNonNull(expr, "expr must not be null");
    Objects.requireNonNull(targetType, "targetType must not be null");

    IrConst stringConst = tryExtractStringLiteralConstant(expr, targetType);
    if (stringConst != null) {
      return stringConst;
    }

    NumericConst numeric = evaluateNumericConstant(expr);
    if (numeric == null) {
      throw new UnsupportedOperationException(
          "Global initializer must be a compile-time constant expression");
    }
    return materializeNumericConstant(numeric, expr, targetType);
  }

  /**
   * Evaluates a global array initializer as an array constant.
   *
   * @param initializer the initializer node
   * @param arrayType the array type
   * @return the array constant
   * @throws UnsupportedOperationException if elements are not compile-time constants
   */
  public static IrConst evaluateGlobalArrayInitializer(
      NonTerminalNode initializer, ArrayType arrayType) {
    return ArrayInitializerEvaluator.evaluateGlobalArrayInitializer(initializer, arrayType);
  }

  /**
   * Parses a character literal.
   *
   * @param lexeme the character literal lexeme (e.g., 'a', '\n')
   * @return the parsed character value
   */
  public static char parseCharLiteral(String lexeme) {
    return LiteralParser.parseCharLiteral(lexeme);
  }

  private static IrConst tryExtractStringLiteralConstant(NonTerminalNode expr, Type targetType) {
    TerminalNode stringLiteral = findStringLiteralTerminal(expr);
    if (stringLiteral == null) {
      return null;
    }

    Type strippedTarget = TypeSystem.stripConst(targetType);
    if (!(strippedTarget instanceof ArrayType arrayType)
        || TypeSystem.stripConst(arrayType.elementType()) != PrimitiveType.CHAR) {
      throw new UnsupportedOperationException(
          "String literal constant requires char array target type");
    }

    List<Character> parsedChars = LiteralParser.parseStringLiteral(stringLiteral.lexeme());
    int declaredSize =
        arrayType.dimensions().isEmpty() ? parsedChars.size() : arrayType.dimensions().get(0);
    if (declaredSize <= 0) {
      declaredSize = parsedChars.size();
    }

    List<IrConst> elements = new ArrayList<>(declaredSize);
    for (int i = 0; i < declaredSize; i++) {
      char value = i < parsedChars.size() ? parsedChars.get(i) : '\0';
      elements.add(new IrConst.CharConst(value));
    }

    IrArrayType irArrayType = (IrArrayType) TypeMapper.toIrType(arrayType);
    return new IrConst.ArrayConst(elements, irArrayType);
  }

  private static TerminalNode findStringLiteralTerminal(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if ("<primarni_izraz>".equals(node.symbol())) {
      if (children.size() == 1 && children.get(0) instanceof TerminalNode term
          && "NIZ_ZNAKOVA".equals(term.symbol())) {
        return term;
      }
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode nested) {
        return findStringLiteralTerminal(nested);
      }
      if (children.size() == 3 && children.get(1) instanceof NonTerminalNode nested) {
        return findStringLiteralTerminal(nested);
      }
      return null;
    }
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode nested) {
      return findStringLiteralTerminal(nested);
    }
    return null;
  }

  private static IrConst materializeNumericConstant(
      NumericConst numeric, NonTerminalNode expr, Type targetType) {
    Type strippedTarget = TypeSystem.stripConst(targetType);
    if (strippedTarget == PrimitiveType.FLOAT) {
      return new IrConst.FloatConst(numeric.asFloat());
    }
    if (strippedTarget == PrimitiveType.CHAR) {
      return new IrConst.CharConst((char) (numeric.asInt() & 0xFF));
    }

    Type sourceType = expr.attributes().type() != null ? expr.attributes().type() : targetType;
    Type strippedSource = TypeSystem.stripConst(sourceType);
    if (strippedSource == PrimitiveType.FLOAT) {
      return new IrConst.FloatConst(numeric.asFloat());
    }
    if (strippedSource == PrimitiveType.CHAR) {
      return new IrConst.CharConst((char) (numeric.asInt() & 0xFF));
    }

    IrType irType = hr.fer.ppj.ir.build.TypeMapper.toIrType(targetType);
    return new IrConst.IntConst(numeric.asInt(), irType);
  }

  private static NumericConst evaluateNumericConstant(NonTerminalNode node) {
    String symbol = node.symbol();
    List<ParseNode> children = node.children();

    if ("<primarni_izraz>".equals(symbol)) {
      return evalPrimary(node, children);
    }
    if ("<cast_izraz>".equals(symbol)) {
      return evalCast(node, children);
    }
    if ("<unarni_izraz>".equals(symbol)) {
      return evalUnary(node, children);
    }
    if ("<izraz>".equals(symbol) && children.size() == 3 && children.get(2) instanceof NonTerminalNode rightExpr) {
      return evaluateNumericConstant(rightExpr);
    }
    if ("<izraz_pridruzivanja>".equals(symbol) && children.size() == 3) {
      return null;
    }
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      return evaluateNumericConstant(child);
    }
    if (children.size() == 3
        && children.get(0) instanceof NonTerminalNode leftNode
        && children.get(1) instanceof TerminalNode op
        && children.get(2) instanceof NonTerminalNode rightNode) {
      NumericConst left = evaluateNumericConstant(leftNode);
      NumericConst right = evaluateNumericConstant(rightNode);
      if (left == null || right == null) {
        return null;
      }
      return applyBinaryOperator(left, right, op);
    }
    return null;
  }

  private static NumericConst evalPrimary(NonTerminalNode node, List<ParseNode> children) {
    if (children.size() == 1 && children.get(0) instanceof TerminalNode term) {
      String symbol = term.symbol();
      String lexeme = term.lexeme();
      if ("BROJ".equals(symbol)) {
        if (lexeme.contains(".") || lexeme.toLowerCase().contains("e")) {
          try {
            return NumericConst.ofFloat(Float.parseFloat(lexeme));
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float literal: " + lexeme, e);
          }
        }
        return NumericConst.ofInt(LiteralParser.parseIntegerLiteral(lexeme));
      }
      if ("ZNAK".equals(symbol)) {
        return NumericConst.ofInt(LiteralParser.parseCharLiteral(lexeme));
      }
      return null;
    }
    if (children.size() == 3 && children.get(1) instanceof NonTerminalNode inner) {
      return evaluateNumericConstant(inner);
    }
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode nested) {
      return evaluateNumericConstant(nested);
    }
    return null;
  }

  private static NumericConst evalCast(NonTerminalNode node, List<ParseNode> children) {
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      return evaluateNumericConstant(child);
    }
    if (children.size() == 4 && children.get(3) instanceof NonTerminalNode castExpr) {
      NumericConst value = evaluateNumericConstant(castExpr);
      if (value == null) {
        return null;
      }
      Type castType = node.attributes().type();
      if (castType == null) {
        return value;
      }
      Type strippedCast = TypeSystem.stripConst(castType);
      if (strippedCast == PrimitiveType.FLOAT) {
        return NumericConst.ofFloat(value.asFloat());
      }
      if (strippedCast == PrimitiveType.CHAR || strippedCast == PrimitiveType.INT) {
        return NumericConst.ofInt(value.asInt());
      }
      return value;
    }
    return null;
  }

  private static NumericConst evalUnary(NonTerminalNode node, List<ParseNode> children) {
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      return evaluateNumericConstant(child);
    }

    String operator = unaryOperator(children);
    if (operator == null || children.size() < 2 || !(children.get(1) instanceof NonTerminalNode operandNode)) {
      return null;
    }
    NumericConst operand = evaluateNumericConstant(operandNode);
    if (operand == null) {
      return null;
    }
    return switch (operator) {
      case "+", "PLUS", "OP_PLUS" -> operand;
      case "-", "MINUS", "OP_MINUS" ->
          operand.isFloat() ? NumericConst.ofFloat(-operand.asFloat()) : NumericConst.ofInt(-operand.asInt());
      case "!" -> NumericConst.ofInt(operand.asInt() == 0 ? 1 : 0);
      case "~" -> NumericConst.ofInt(~operand.asInt());
      default -> null;
    };
  }

  private static String unaryOperator(List<ParseNode> children) {
    ParseNode first = children.get(0);
    if (first instanceof TerminalNode opTerm) {
      return !opTerm.lexeme().isEmpty() ? opTerm.lexeme() : opTerm.symbol();
    }
    if (first instanceof NonTerminalNode unaryOperatorNode
        && "<unarni_operator>".equals(unaryOperatorNode.symbol())
        && !unaryOperatorNode.children().isEmpty()
        && unaryOperatorNode.children().get(0) instanceof TerminalNode opTerm) {
      return !opTerm.lexeme().isEmpty() ? opTerm.lexeme() : opTerm.symbol();
    }
    return null;
  }

  private static NumericConst applyBinaryOperator(
      NumericConst left, NumericConst right, TerminalNode operatorToken) {
    String lexeme = operatorToken.lexeme();
    String symbol = operatorToken.symbol();

    switch (lexeme) {
      case "+":
        return arithmetic(left, right, ArithmeticOp.ADD);
      case "-":
        return arithmetic(left, right, ArithmeticOp.SUB);
      case "*":
        return arithmetic(left, right, ArithmeticOp.MUL);
      case "/":
        return arithmetic(left, right, ArithmeticOp.DIV);
      case "%":
        return arithmetic(left, right, ArithmeticOp.MOD);
      case "==":
        return NumericConst.ofInt(compare(left, right) == 0 ? 1 : 0);
      case "!=":
        return NumericConst.ofInt(compare(left, right) != 0 ? 1 : 0);
      case "<":
        return NumericConst.ofInt(compare(left, right) < 0 ? 1 : 0);
      case "<=":
        return NumericConst.ofInt(compare(left, right) <= 0 ? 1 : 0);
      case ">":
        return NumericConst.ofInt(compare(left, right) > 0 ? 1 : 0);
      case ">=":
        return NumericConst.ofInt(compare(left, right) >= 0 ? 1 : 0);
      case "&":
        return NumericConst.ofInt(left.asInt() & right.asInt());
      case "|":
        return NumericConst.ofInt(left.asInt() | right.asInt());
      case "^":
        return NumericConst.ofInt(left.asInt() ^ right.asInt());
      case "&&":
        return NumericConst.ofInt((left.asInt() != 0 && right.asInt() != 0) ? 1 : 0);
      case "||":
        return NumericConst.ofInt((left.asInt() != 0 || right.asInt() != 0) ? 1 : 0);
      default:
        return applyBinaryOperatorBySymbol(left, right, symbol);
    }
  }

  private static NumericConst applyBinaryOperatorBySymbol(
      NumericConst left, NumericConst right, String symbol) {
    return switch (symbol) {
      case "PLUS" -> arithmetic(left, right, ArithmeticOp.ADD);
      case "MINUS" -> arithmetic(left, right, ArithmeticOp.SUB);
      case "ASTERISK" -> arithmetic(left, right, ArithmeticOp.MUL);
      case "OP_DIJELI" -> arithmetic(left, right, ArithmeticOp.DIV);
      case "OP_MOD" -> arithmetic(left, right, ArithmeticOp.MOD);
      case "OP_EQ" -> NumericConst.ofInt(compare(left, right) == 0 ? 1 : 0);
      case "OP_NEQ" -> NumericConst.ofInt(compare(left, right) != 0 ? 1 : 0);
      case "OP_LT" -> NumericConst.ofInt(compare(left, right) < 0 ? 1 : 0);
      case "OP_LTE" -> NumericConst.ofInt(compare(left, right) <= 0 ? 1 : 0);
      case "OP_GT" -> NumericConst.ofInt(compare(left, right) > 0 ? 1 : 0);
      case "OP_GTE" -> NumericConst.ofInt(compare(left, right) >= 0 ? 1 : 0);
      case "OP_BIN_I" -> NumericConst.ofInt(left.asInt() & right.asInt());
      case "OP_BIN_ILI" -> NumericConst.ofInt(left.asInt() | right.asInt());
      case "OP_BIN_XILI" -> NumericConst.ofInt(left.asInt() ^ right.asInt());
      case "OP_I" -> NumericConst.ofInt((left.asInt() != 0 && right.asInt() != 0) ? 1 : 0);
      case "OP_ILI" -> NumericConst.ofInt((left.asInt() != 0 || right.asInt() != 0) ? 1 : 0);
      default -> null;
    };
  }

  private static NumericConst arithmetic(NumericConst left, NumericConst right, ArithmeticOp op) {
    boolean isFloat = left.isFloat() || right.isFloat();
    if (isFloat) {
      float l = left.asFloat();
      float r = right.asFloat();
      return switch (op) {
        case ADD -> NumericConst.ofFloat(l + r);
        case SUB -> NumericConst.ofFloat(l - r);
        case MUL -> NumericConst.ofFloat(l * r);
        case DIV -> {
          if (r == 0.0f) {
            throw new IllegalArgumentException("Division by zero in constant expression");
          }
          yield NumericConst.ofFloat(l / r);
        }
        case MOD -> {
          if (r == 0.0f) {
            throw new IllegalArgumentException("Modulo by zero in constant expression");
          }
          yield NumericConst.ofFloat(l % r);
        }
      };
    }

    int l = left.asInt();
    int r = right.asInt();
    return switch (op) {
      case ADD -> NumericConst.ofInt(l + r);
      case SUB -> NumericConst.ofInt(l - r);
      case MUL -> NumericConst.ofInt(l * r);
      case DIV -> {
        if (r == 0) {
          throw new IllegalArgumentException("Division by zero in constant expression");
        }
        yield NumericConst.ofInt(l / r);
      }
      case MOD -> {
        if (r == 0) {
          throw new IllegalArgumentException("Modulo by zero in constant expression");
        }
        yield NumericConst.ofInt(l % r);
      }
    };
  }

  private static int compare(NumericConst left, NumericConst right) {
    if (left.isFloat() || right.isFloat()) {
      return Float.compare(left.asFloat(), right.asFloat());
    }
    return Integer.compare(left.asInt(), right.asInt());
  }

  private enum ArithmeticOp {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD
  }

  private record NumericConst(boolean isFloat, int intValue, float floatValue) {
    private static NumericConst ofInt(int value) {
      return new NumericConst(false, value, value);
    }

    private static NumericConst ofFloat(float value) {
      return new NumericConst(true, (int) value, value);
    }

    private int asInt() {
      return isFloat ? (int) floatValue : intValue;
    }

    private float asFloat() {
      return isFloat ? floatValue : intValue;
    }
  }
}
