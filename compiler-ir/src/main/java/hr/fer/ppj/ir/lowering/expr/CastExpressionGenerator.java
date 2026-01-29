package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.util.CastOperationDeterminer;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for cast expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class CastExpressionGenerator {

  private final ExpressionEmitter emitter;

  public CastExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
  }

  /**
   * Emits r-value for a cast expression.
   *
   * <p><cast_izraz> can be:
   * <ul>
   *   <li><unarni_izraz> (no cast, just pass through)</li>
   *   <li>L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz> (explicit cast)</li>
   * </ul>
   */
  public IrValue emitRValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();

    // Case 1: Explicit cast: L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
    if (children.size() >= 4) {
      NonTerminalNode operand = NodeUtils.asNonTerminal(children.get(3), "<cast_izraz>");
      IrValue operandValue = emitter.emitRValue(operand, functionContext);

      SemanticAttributes attrs = node.attributes();
      Type targetType = attrs.type();
      if (targetType != null) {
        IrType irTargetType = TypeMapper.toIrType(targetType);

        // Get operand type
        Type operandType = operand.attributes().type();
        IrType irOperandType =
            operandType != null
                ? TypeMapper.toIrType(operandType)
                : (operandValue instanceof IrTemp temp
                    ? temp.type()
                    : operandValue instanceof IrConst c ? c.type() : null);

        // If types match, no cast needed
        if (irOperandType != null && irOperandType.equals(irTargetType)) {
          return operandValue;
        }

        // Determine cast operation based on types
        if (irOperandType != null) {
          IrRhs.CastOp.CastName castName =
              CastOperationDeterminer.determineCastOperation(irOperandType, irTargetType);
          if (castName != null) {
            IrFunctionBuilder builder = functionContext.functionBuilder();
            IrRhs.CastOp castOp = new IrRhs.CastOp(castName, operandValue, irTargetType);
            IrTemp result = builder.tempFactory().newTemp(irTargetType);
            builder.addInstruction(new IrInstruction.IrAssignInstr(result, castOp));
            return result;
          }
        }
      }
      return operandValue;
    }

    // Case 2: No cast, just <unarni_izraz> - pass through
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode nt) {
      return emitter.emitRValue(nt, functionContext);
    }

    throw new IllegalArgumentException(
        "Invalid cast expression: " + children.size() + " children");
  }
}
