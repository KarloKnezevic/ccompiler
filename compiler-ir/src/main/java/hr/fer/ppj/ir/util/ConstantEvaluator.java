package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
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
    
    String symbol = expr.symbol();
    
    // Check if it's a primary expression with a literal
    if (symbol.equals("<primarni_izraz>")) {
      List<ParseNode> children = expr.children();
      if (!children.isEmpty() && children.get(0) instanceof TerminalNode term) {
        String termSymbol = term.symbol();
        String lexeme = term.lexeme();
        SemanticAttributes attrs = expr.attributes();
        Type constType = attrs.type() != null ? attrs.type() : targetType;
        IrType irType = hr.fer.ppj.ir.build.TypeMapper.toIrType(constType);
        
        // Only extract constants from literals, NOT from variables (IDN)
        if (termSymbol.equals("IDN")) {
          throw new UnsupportedOperationException("Cannot extract constant from variable: " + lexeme);
        }
        
        if (termSymbol.equals("BROJ")) {
          if (constType == PrimitiveType.INT) {
            try {
              int value = LiteralParser.parseIntegerLiteral(lexeme);
              return new IrConst.IntConst(value, irType);
            } catch (IllegalArgumentException e) {
              throw new IllegalArgumentException("Invalid integer literal: " + lexeme);
            }
          } else if (constType == PrimitiveType.FLOAT) {
            try {
              float value = Float.parseFloat(lexeme);
              return new IrConst.FloatConst(value);
            } catch (NumberFormatException e) {
              throw new IllegalArgumentException("Invalid float literal: " + lexeme);
            }
          }
        } else if (termSymbol.equals("ZNAK")) {
          char value = LiteralParser.parseCharLiteral(lexeme);
          return new IrConst.CharConst(value);
        } else if (termSymbol.equals("NIZ_ZNAKOVA")) {
          Type strippedTarget = TypeSystem.stripConst(targetType);
          if (strippedTarget instanceof ArrayType arrayType
              && TypeSystem.stripConst(arrayType.elementType()) == PrimitiveType.CHAR) {
            List<Character> parsedChars = LiteralParser.parseStringLiteral(lexeme);
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
          throw new UnsupportedOperationException(
              "String literal constant requires char array target type");
        }
      }
    }
    
    // Handle unary minus: <unarni_izraz> with MINUS operator
    if (symbol.equals("<unarni_izraz>")) {
      List<ParseNode> children = expr.children();
      if (children.size() >= 2) {
        ParseNode firstChild = children.get(0);
        String op = null;
        NonTerminalNode operandNode = null;
        
        if (firstChild instanceof TerminalNode term && term.symbol().equals("MINUS")) {
          op = "MINUS";
          if (children.size() >= 2 && children.get(1) instanceof NonTerminalNode nt) {
            operandNode = NodeUtils.asNonTerminal(nt, "<cast_izraz>");
          }
        } else if (firstChild instanceof NonTerminalNode nt && nt.symbol().equals("<unarni_operator>")) {
          List<ParseNode> opChildren = nt.children();
          if (!opChildren.isEmpty() && opChildren.get(0) instanceof TerminalNode opTerm) {
            op = opTerm.symbol();
          }
          if (children.size() >= 2 && children.get(1) instanceof NonTerminalNode castNode) {
            operandNode = NodeUtils.asNonTerminal(castNode, "<cast_izraz>");
          }
        }
        
        if (op != null && op.equals("MINUS") && operandNode != null) {
          IrConst operandConst = extractConstantFromExpression(operandNode, targetType);
          if (operandConst instanceof IrConst.IntConst intConst) {
            return new IrConst.IntConst(-intConst.value(), intConst.type());
          } else if (operandConst instanceof IrConst.FloatConst floatConst) {
            return new IrConst.FloatConst(-floatConst.value());
          }
        }
      }
    }
    
    // For other expression types, recursively search for a primary expression with a literal
    SemanticAttributes attrs = expr.attributes();
    Type constType = attrs.type() != null ? attrs.type() : targetType;
    for (ParseNode child : expr.children()) {
      if (child instanceof NonTerminalNode nt) {
        try {
          IrConst result = extractConstantFromExpression(nt, constType);
          if (result != null) {
            return result;
          }
        } catch (UnsupportedOperationException e) {
          // Continue searching in other children
        }
      }
    }
    
    throw new UnsupportedOperationException("Global initializer must be a compile-time constant expression");
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
}
