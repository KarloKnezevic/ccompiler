package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.OperatorMapper;
import hr.fer.ppj.ir.util.TypePromoter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for binary expressions (multiplicative and additive).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BinaryExpressionGenerator {

  private final ExpressionEmitter emitter;
  private final PostfixIncrementHandler postfixHandler;

  public BinaryExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    LValueEmitter lValueEmitter = (emitter instanceof LValueEmitter lv) ? lv : null;
    this.postfixHandler = new PostfixIncrementHandler(emitter, lValueEmitter);
  }

  /**
   * Emits r-value for a multiplicative expression (*, /, %).
   */
  public IrValue emitMultiplicative(NonTerminalNode node, FunctionContext ctx) {
    return emitBinary(node, ctx,
        new String[] {"OP_PUTA", "ASTERISK", "OP_DIJELI", "OP_MOD"},
        new IrRhs.BinOp.BinOpName[] {
            IrRhs.BinOp.BinOpName.MUL, IrRhs.BinOp.BinOpName.MUL, 
            IrRhs.BinOp.BinOpName.DIV, IrRhs.BinOp.BinOpName.MOD
        });
  }

  /**
   * Emits r-value for an additive expression (+, -).
   */
  public IrValue emitAdditive(NonTerminalNode node, FunctionContext ctx) {
    return emitBinary(node, ctx,
        new String[] {"PLUS", "MINUS"},
        new IrRhs.BinOp.BinOpName[] {IrRhs.BinOp.BinOpName.ADD, IrRhs.BinOp.BinOpName.SUB});
  }

  private IrValue emitBinary(NonTerminalNode node, FunctionContext ctx,
      String[] opSymbols, IrRhs.BinOp.BinOpName[] opNames) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      if (children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, ctx);
      }
      throw new IllegalArgumentException("Invalid binary expression: single child is not a non-terminal");
    }

    if (children.size() != 3) {
      throw new IllegalStateException("Binary expression must have 1 or 3 children, but has "
          + children.size() + ": " + node.symbol());
    }

    TerminalNode opTerm = extractOperator(children.get(1), node.symbol());
    String opSymbol = opTerm.symbol();

    NonTerminalNode leftNode = extractOperand(children.get(0), "left");
    NonTerminalNode rightNode = extractOperand(children.get(2), "right");

    IrType leftIrType = TypeMapper.toIrType(leftNode.attributes().type());
    IrType rightIrType = TypeMapper.toIrType(rightNode.attributes().type());
    IrFunctionBuilder builder = ctx.functionBuilder();

    // Check for pointer arithmetic: ptr + int, int + ptr, or ptr - int
    if ((opSymbol.equals("PLUS") || opSymbol.equals("MINUS")) 
        && (leftIrType instanceof hr.fer.ppj.ir.types.IrPointerType || rightIrType instanceof hr.fer.ppj.ir.types.IrPointerType)) {
      return emitPointerArithmetic(leftNode, rightNode, opSymbol, leftIrType, rightIrType, ctx, builder);
    }

    IrType irResultType = determineResultType(leftNode, rightNode);
    IrValue left, right;

    if (shouldEvaluateRightFirst(leftNode, rightNode)) {
      right = evaluateOperand(rightNode, rightIrType, irResultType, ctx, builder);
      ctx.addressReuseContext().clearAllLastLoadedValues();
      left = evaluateOperand(leftNode, leftIrType, irResultType, ctx, builder);
    } else if (shouldEvaluateRightFirstForArrayIndex(leftNode, rightNode)) {
      right = evaluateOperand(rightNode, rightIrType, irResultType, ctx, builder);
      left = evaluateOperand(leftNode, leftIrType, irResultType, ctx, builder);
    } else {
      left = evaluateOperand(leftNode, leftIrType, irResultType, ctx, builder);
      if (opSymbol.equals("PLUS") || opSymbol.equals("MINUS")) {
        ctx.addressReuseContext().clearAllLastLoadedValues();
      }
      right = evaluateRightOperand(rightNode, rightIrType, irResultType, ctx, builder);
    }

    IrRhs.BinOp.BinOpName binOpName = mapOperator(opSymbol, opSymbols, opNames, node.symbol());
    IrRhs.BinOp binOp = new IrRhs.BinOp(binOpName, left, right, irResultType);
    IrTemp result = builder.tempFactory().newTemp(irResultType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, binOp));

    if (ExpressionAnalyzer.containsPostfixIncrement(rightNode)) {
      postfixHandler.performPostfixIncrement(rightNode, ctx);
    }

    if (binOpName == IrRhs.BinOp.BinOpName.ADD || binOpName == IrRhs.BinOp.BinOpName.SUB) {
      ctx.addressReuseContext().clearAllLastLoadedValues();
    }

    return result;
  }

  private TerminalNode extractOperator(ParseNode opNode, String nodeSymbol) {
    if (!(opNode instanceof TerminalNode term)) {
      throw new IllegalStateException("Binary expression operator must be a terminal node in " + nodeSymbol);
    }
    return term;
  }

  private NonTerminalNode extractOperand(ParseNode node, String side) {
    if (!(node instanceof NonTerminalNode nt)) {
      throw new IllegalArgumentException(side + " operand must be a non-terminal");
    }
    return nt;
  }

  private IrType determineResultType(NonTerminalNode leftNode, NonTerminalNode rightNode) {
    Type leftType = leftNode.attributes().type();
    Type rightType = rightNode.attributes().type();
    Type resultType;
    if (leftType != null && rightType != null && leftType.isScalar() && rightType.isScalar()) {
      resultType = TypeSystem.arithmeticResult(leftType, rightType);
    } else {
      resultType = leftType != null ? leftType : rightType;
    }
    return TypeMapper.toIrType(resultType);
  }

  private boolean shouldEvaluateRightFirst(NonTerminalNode leftNode, NonTerminalNode rightNode) {
    boolean leftContainsCall = ExpressionAnalyzer.containsFunctionCall(leftNode);
    boolean rightContainsCall = ExpressionAnalyzer.containsFunctionCall(rightNode);
    boolean rightIsSimpleVar = ExpressionAnalyzer.isSimpleVariable(rightNode);
    return leftContainsCall && !rightContainsCall && rightIsSimpleVar;
  }

  private boolean shouldEvaluateRightFirstForArrayIndex(NonTerminalNode leftNode, NonTerminalNode rightNode) {
    boolean leftContainsCall = ExpressionAnalyzer.containsFunctionCall(leftNode);
    boolean rightContainsCall = ExpressionAnalyzer.containsFunctionCall(rightNode);
    boolean leftIsSimpleVar = ExpressionAnalyzer.isSimpleVariable(leftNode);
    boolean rightHasArrayIndex = ExpressionAnalyzer.containsArrayIndexing(rightNode);
    return leftIsSimpleVar && !leftContainsCall && rightHasArrayIndex && !rightContainsCall;
  }

  private IrValue evaluateOperand(NonTerminalNode node, IrType nodeType, IrType resultType,
      FunctionContext ctx, IrFunctionBuilder builder) {
    IrValue value = emitter.emitRValue(node, ctx);
    if (!nodeType.equals(resultType)) {
      return TypePromoter.promoteValue(value, nodeType, resultType, builder);
    }
    return value;
  }

  private IrValue evaluateRightOperand(NonTerminalNode node, IrType nodeType, IrType resultType,
      FunctionContext ctx, IrFunctionBuilder builder) {
    boolean hasPostfixInc = ExpressionAnalyzer.containsPostfixIncrement(node);
    IrValue value;
    if (hasPostfixInc) {
      value = postfixHandler.loadValueForPostfixIncrement(node, ctx);
    } else {
      value = emitter.emitRValue(node, ctx);
    }
    if (!nodeType.equals(resultType)) {
      return TypePromoter.promoteValue(value, nodeType, resultType, builder);
    }
    return value;
  }

  private IrRhs.BinOp.BinOpName mapOperator(String opSymbol, String[] opSymbols,
      IrRhs.BinOp.BinOpName[] opNames, String nodeSymbol) {
    for (int i = 0; i < opSymbols.length && i < opNames.length; i++) {
      if (opSymbol.equals(opSymbols[i])) {
        return opNames[i];
      }
    }
    try {
      return OperatorMapper.mapBinaryOperator(opSymbol);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Cannot map operator '" + opSymbol + "' in " + nodeSymbol, e);
    }
  }

  /**
   * Emits IR for pointer arithmetic: ptr + int, int + ptr, or ptr - int.
   * Uses ptrcast to convert between pointer and integer for the arithmetic.
   */
  private IrValue emitPointerArithmetic(NonTerminalNode leftNode, NonTerminalNode rightNode,
      String opSymbol, IrType leftIrType, IrType rightIrType, FunctionContext ctx, 
      IrFunctionBuilder builder) {
    
    // Determine which operand is the pointer and which is the integer
    NonTerminalNode ptrNode;
    NonTerminalNode intNode;
    IrPointerType ptrType;
    
    if (leftIrType instanceof IrPointerType leftPtrType) {
      ptrNode = leftNode;
      intNode = rightNode;
      ptrType = leftPtrType;
    } else if (rightIrType instanceof IrPointerType rightPtrType) {
      ptrNode = rightNode;
      intNode = leftNode;
      ptrType = rightPtrType;
    } else {
      throw new IllegalStateException("No pointer type in pointer arithmetic");
    }
    
    // Get the element size for scaling
    int elemSize = hr.fer.ppj.ir.build.TypeSizeCalculator.getTypeSize(ptrType.baseType());
    
    // Emit the pointer value
    IrValue ptrValue = emitter.emitRValue(ptrNode, ctx);
    
    // Cast pointer to int32 using ptrcast
    IrRhs.CastOp ptrToIntCast = new IrRhs.CastOp(
        IrRhs.CastName.PTRCAST, ptrValue, IrPrimitiveType.INT32);
    IrTemp ptrAsInt = builder.tempFactory().newTemp(IrPrimitiveType.INT32);
    builder.addInstruction(new IrInstruction.IrAssignInstr(ptrAsInt, ptrToIntCast));
    
    // Emit the integer value and scale it by element size
    IrValue intValue = emitter.emitRValue(intNode, ctx);
    IrValue scaledOffset;
    if (elemSize != 1) {
      // If the integer value is a constant, fold the multiplication
      if (intValue instanceof IrConst.IntConst intConst) {
        int scaledValue = (int) intConst.value() * elemSize;
        scaledOffset = new IrConst.IntConst(scaledValue, IrPrimitiveType.INT32);
      } else {
        // Scale the offset by element size
        IrConst sizeConst = new IrConst.IntConst(elemSize, IrPrimitiveType.INT32);
        IrRhs.BinOp scaleOp = new IrRhs.BinOp(
            IrRhs.BinOp.BinOpName.MUL, intValue, sizeConst, IrPrimitiveType.INT32);
        IrTemp scaledTemp = builder.tempFactory().newTemp(IrPrimitiveType.INT32);
        builder.addInstruction(new IrInstruction.IrAssignInstr(scaledTemp, scaleOp));
        scaledOffset = scaledTemp;
      }
    } else {
      scaledOffset = intValue;
    }
    
    // Add or subtract the scaled offset
    IrRhs.BinOp.BinOpName binOp = opSymbol.equals("PLUS") 
        ? IrRhs.BinOp.BinOpName.ADD 
        : IrRhs.BinOp.BinOpName.SUB;
    IrRhs.BinOp arithmetic = new IrRhs.BinOp(binOp, ptrAsInt, scaledOffset, IrPrimitiveType.INT32);
    IrTemp resultInt = builder.tempFactory().newTemp(IrPrimitiveType.INT32);
    builder.addInstruction(new IrInstruction.IrAssignInstr(resultInt, arithmetic));
    
    // Cast the result back to pointer using ptrcast
    IrRhs.CastOp intToPtrCast = new IrRhs.CastOp(
        IrRhs.CastName.PTRCAST, resultInt, ptrType);
    IrTemp resultPtr = builder.tempFactory().newTemp(ptrType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(resultPtr, intToPtrCast));
    
    return resultPtr;
  }
}
