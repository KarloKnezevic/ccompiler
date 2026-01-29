package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.model.IrRhs;
import java.util.Objects;

/**
 * Maps terminal operator symbols to IR operation names.
 *
 * <p>This utility provides mapping functions for:
 * <ul>
 *   <li>Binary operators (arithmetic, bitwise)</li>
 *   <li>Comparison operators</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class OperatorMapper {

  private OperatorMapper() {}

  /**
   * Maps a terminal operator symbol to a binary operation name.
   *
   * @param opSymbol the operator symbol (e.g., "PLUS", "OP_PUTA")
   * @return the IR binary operation name
   * @throws IllegalArgumentException if the operator is unknown
   */
  public static IrRhs.BinOp.BinOpName mapBinaryOperator(String opSymbol) {
    Objects.requireNonNull(opSymbol, "opSymbol must not be null");
    
    return switch (opSymbol) {
      case "PLUS" -> IrRhs.BinOp.BinOpName.ADD;
      case "MINUS" -> IrRhs.BinOp.BinOpName.SUB;
      case "OP_PUTA" -> IrRhs.BinOp.BinOpName.MUL;
      case "OP_DIJELI" -> IrRhs.BinOp.BinOpName.DIV;
      case "OP_MOD" -> IrRhs.BinOp.BinOpName.MOD;
      case "AMPERSAND" -> IrRhs.BinOp.BinOpName.AND;
      case "OP_BIN_XILI" -> IrRhs.BinOp.BinOpName.XOR;
      case "OP_BIN_ILI" -> IrRhs.BinOp.BinOpName.OR;
      default -> throw new IllegalArgumentException("Unknown binary operator: " + opSymbol);
    };
  }

  /**
   * Maps a terminal operator symbol to a comparison operation name.
   *
   * @param opSymbol the operator symbol (e.g., "OP_EQ", "OP_LT")
   * @return the IR comparison operation name
   * @throws IllegalArgumentException if the operator is unknown
   */
  public static IrRhs.CmpOp.CmpOpName mapComparisonOperator(String opSymbol) {
    Objects.requireNonNull(opSymbol, "opSymbol must not be null");
    
    return switch (opSymbol) {
      case "OP_EQ" -> IrRhs.CmpOp.CmpOpName.EQ;
      case "OP_NEQ" -> IrRhs.CmpOp.CmpOpName.NE;
      case "OP_LT" -> IrRhs.CmpOp.CmpOpName.LT;
      case "OP_LTE" -> IrRhs.CmpOp.CmpOpName.LE;
      case "OP_GT" -> IrRhs.CmpOp.CmpOpName.GT;
      case "OP_GTE" -> IrRhs.CmpOp.CmpOpName.GE;
      default -> throw new IllegalArgumentException("Unknown comparison operator: " + opSymbol);
    };
  }
}
