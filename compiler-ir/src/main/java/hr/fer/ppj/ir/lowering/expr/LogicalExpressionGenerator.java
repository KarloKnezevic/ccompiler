package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.build.TypeSizeCalculator;
import hr.fer.ppj.ir.build.TypeAlignmentCalculator;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for logical expressions (short-circuit evaluation).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LogicalExpressionGenerator {

  private final ExpressionEmitter emitter;

  public LogicalExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
  }

  /**
   * Emits a logical expression for branching (used in if conditions).
   *
   * <p>Branches directly to trueLabel/falseLabel without materializing a result.
   */
  public void emitRValueLogicalForBranching(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      String trueLabel,
      String falseLabel) {
    List<ParseNode> children = node.children();
    IrFunctionBuilder builder = functionContext.functionBuilder();

    if (children.size() < 3) {
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
        IrValue value = emitter.emitRValue(nt, functionContext);
        Type valueType = nt.attributes().type();
        IrValue boolValue = convertToBool(value, valueType, builder);
        builder.setTerminator(new IrTerminator.IrBrTerm(boolValue, trueLabel, falseLabel));
        return;
      }
      throw new IllegalArgumentException("Cannot emit logical expression for branching");
    }

    ParseNode opNode = children.get(1);
    if (!(opNode instanceof TerminalNode term)) {
      throw new IllegalArgumentException("Logical operator must be a terminal");
    }

    String op = term.symbol();
    boolean isOr = op.equals("OP_ILI");
    boolean isAnd = op.equals("OP_I");

    if (!isOr && !isAnd) {
      throw new IllegalArgumentException("Unknown logical operator: " + op);
    }

    ParseNode leftParseNode = children.get(0);
    ParseNode rightParseNode = children.get(2);
    if (!(leftParseNode instanceof NonTerminalNode leftNode)
        || !(rightParseNode instanceof NonTerminalNode rightNode)) {
      throw new IllegalArgumentException("Logical operands must be non-terminals");
    }

    IrValue leftValue = emitter.emitRValue(leftNode, functionContext);
    Type leftType = leftNode.attributes().type();
    IrValue leftBool = convertToBool(leftValue, leftType, builder);

    if (isOr) {
      // For OR: if left is true, go to trueLabel; otherwise evaluate right side in falseLabel
      // The right side evaluation happens in falseLabel, and then branches to trueLabel if true
      builder.setTerminator(new IrTerminator.IrBrTerm(leftBool, trueLabel, falseLabel));
      // Note: We don't start falseLabel here - it will be started by IfStatementGenerator
      // after generating the then branch, to ensure correct block ordering (L1 before L2)
    } else {
      // For AND: if left is false, go to falseLabel; otherwise evaluate right side in trueLabel
      // The right side evaluation happens in trueLabel (the then branch), then branches based on result
      builder.setTerminator(new IrTerminator.IrBrTerm(leftBool, trueLabel, falseLabel));
      // Note: We don't start trueLabel here - it will be started by IfStatementGenerator
      // which will evaluate the right side there
    }
  }

  /**
   * Emits r-value for a logical expression with short-circuit evaluation.
   *
   * <p>Materializes the bool result using a local slot to avoid cross-block temps.
   * This ensures temps are only used within a single block.
   */
  public IrValue emitRValueLogical(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      boolean materializeResult) {
    List<ParseNode> children = node.children();
    IrFunctionBuilder builder = functionContext.functionBuilder();

    if (children.size() < 3) {
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, functionContext);
      }
      throw new IllegalArgumentException("Cannot emit r-value for logical expression");
    }

    ParseNode opNode = children.get(1);
    if (!(opNode instanceof TerminalNode term)) {
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, functionContext);
      }
      throw new IllegalArgumentException("Logical operator must be a terminal");
    }

    String op = term.symbol();
    boolean isOr = op.equals("OP_ILI");
    boolean isAnd = op.equals("OP_I");

    if (!isOr && !isAnd) {
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, functionContext);
      }
      throw new IllegalArgumentException("Unknown logical operator: " + op);
    }

    ParseNode leftParseNode = children.get(0);
    ParseNode rightParseNode = children.get(2);
    if (!(leftParseNode instanceof NonTerminalNode leftNode)) {
      throw new IllegalArgumentException("Left operand of logical expression must be non-terminal");
    }
    if (!(rightParseNode instanceof NonTerminalNode rightNode)) {
      throw new IllegalArgumentException("Right operand of logical expression must be non-terminal");
    }

    if (materializeResult) {
      return emitMaterialized(node, leftNode, rightNode, isOr, functionContext);
    } else {
      // For expression statements, just evaluate for side effects
      // Use branching to short-circuit, but don't materialize result
      emitForSideEffects(leftNode, rightNode, isOr, functionContext);
      return new IrConst.IntConst(0, IrPrimitiveType.BOOL);
    }
  }

  /**
   * Emits a materialized logical expression result using a local slot.
   * This avoids cross-block temp usage by storing the result in a slot.
   */
  private IrValue emitMaterialized(
      NonTerminalNode node,
      NonTerminalNode leftNode,
      NonTerminalNode rightNode,
      boolean isOr,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();
    IrType boolType = IrPrimitiveType.BOOL;

    // Create labels
    String evalRightLabel = builder.labelFactory().newLabel();
    String mergeLabel = builder.labelFactory().newLabel();
    String trueLabel = builder.labelFactory().newLabel();
    String falseLabel = builder.labelFactory().newLabel();

    // Allocate a local slot for the result
    String resultVarName = "__logical_result_" + functionContext.incrementLogicalResultCounter();
    int varSize = TypeSizeCalculator.getTypeSize(boolType);
    int varAlign = TypeAlignmentCalculator.getTypeAlignment(boolType);
    int currentOffset = functionContext.localOffset();
    currentOffset = (currentOffset + varAlign - 1) / varAlign * varAlign;
    int resultOffset = currentOffset;
    currentOffset += varSize;
    functionContext.setLocalOffset(currentOffset);
    IrSlot resultSlot = new IrSlot(IrSlot.Kind.LOCAL, resultVarName, resultOffset, boolType);
    builder.addSlot(resultSlot);

    // Evaluate left side and convert to bool
    IrValue leftValue = emitter.emitRValue(leftNode, functionContext);
    Type leftType = leftNode.attributes().type();
    IrValue leftBool = convertToBool(leftValue, leftType, builder);

    // Branch based on left side
    if (isOr) {
      // OR: if left is true, go to trueLabel; otherwise evaluate right
      builder.setTerminator(new IrTerminator.IrBrTerm(leftBool, trueLabel, evalRightLabel));
    } else {
      // AND: if left is false, go to falseLabel; otherwise evaluate right
      builder.setTerminator(new IrTerminator.IrBrTerm(leftBool, evalRightLabel, falseLabel));
    }

    // Evaluate right side
    builder.startBlock(evalRightLabel);
    IrValue rightValue = emitter.emitRValue(rightNode, functionContext);
    Type rightType = rightNode.attributes().type();
    IrValue rightBool = convertToBool(rightValue, rightType, builder);
    builder.setTerminator(new IrTerminator.IrBrTerm(rightBool, trueLabel, falseLabel));

    // True branch: store 1
    builder.startBlock(trueLabel);
    IrConst trueConst = new IrConst.IntConst(1, IrPrimitiveType.BOOL);
    IrTemp trueAddr = builder.tempFactory().newTemp(new IrPointerType(boolType));
    IrRhs.AddrOfSymbol trueAddrOf =
        new IrRhs.AddrOfSymbol(
            new IrSymbolRef(IrSymbolRef.Kind.LOCAL, resultVarName), new IrPointerType(boolType));
    builder.addInstruction(new IrInstruction.IrAssignInstr(trueAddr, trueAddrOf));
    builder.addInstruction(new IrInstruction.IrStoreInstr(trueAddr, trueConst, boolType));
    builder.setTerminator(new IrTerminator.IrJmpTerm(mergeLabel));

    // False branch: store 0
    builder.startBlock(falseLabel);
    IrConst falseConst = new IrConst.IntConst(0, IrPrimitiveType.BOOL);
    IrTemp falseAddr = builder.tempFactory().newTemp(new IrPointerType(boolType));
    IrRhs.AddrOfSymbol falseAddrOf =
        new IrRhs.AddrOfSymbol(
            new IrSymbolRef(IrSymbolRef.Kind.LOCAL, resultVarName), new IrPointerType(boolType));
    builder.addInstruction(new IrInstruction.IrAssignInstr(falseAddr, falseAddrOf));
    builder.addInstruction(new IrInstruction.IrStoreInstr(falseAddr, falseConst, boolType));
    builder.setTerminator(new IrTerminator.IrJmpTerm(mergeLabel));

    // Merge block: load result
    builder.startBlock(mergeLabel);
    IrTemp mergeAddr = builder.tempFactory().newTemp(new IrPointerType(boolType));
    IrRhs.AddrOfSymbol mergeAddrOf =
        new IrRhs.AddrOfSymbol(
            new IrSymbolRef(IrSymbolRef.Kind.LOCAL, resultVarName), new IrPointerType(boolType));
    builder.addInstruction(new IrInstruction.IrAssignInstr(mergeAddr, mergeAddrOf));
    IrRhs.Load loadResult = new IrRhs.Load(mergeAddr, boolType);
    IrTemp finalResult = builder.tempFactory().newTemp(boolType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(finalResult, loadResult));
    return finalResult;
  }

  /**
   * Emits a logical expression for side effects only (expression statements).
   * Evaluates both sides with short-circuit, but doesn't materialize a result.
   */
  private void emitForSideEffects(
      NonTerminalNode leftNode,
      NonTerminalNode rightNode,
      boolean isOr,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();

    // Create labels in correct order based on operator
    String shortCircuitLabel;
    String evalRightLabel;
    String mergeLabel;
    
    if (isOr) {
      // OR: short-circuit label first (L1), then eval right (L2), then merge (L3)
      shortCircuitLabel = builder.labelFactory().newLabel();
      evalRightLabel = builder.labelFactory().newLabel();
      mergeLabel = builder.labelFactory().newLabel();
    } else {
      // AND: eval right label first (L4), then short-circuit (L5), then merge (L6)
      evalRightLabel = builder.labelFactory().newLabel();
      shortCircuitLabel = builder.labelFactory().newLabel();
      mergeLabel = builder.labelFactory().newLabel();
    }

    // Evaluate left side and convert to bool
    IrValue leftValue = emitter.emitRValue(leftNode, functionContext);
    Type leftType = leftNode.attributes().type();
    IrValue leftBool = convertToBool(leftValue, leftType, builder);

    // Branch based on left side
    if (isOr) {
      // OR: if left is true, go to short-circuit label; otherwise evaluate right
      builder.setTerminator(new IrTerminator.IrBrTerm(leftBool, shortCircuitLabel, evalRightLabel));
    } else {
      // AND: if left is false, go to short-circuit label; otherwise evaluate right
      builder.setTerminator(new IrTerminator.IrBrTerm(leftBool, evalRightLabel, shortCircuitLabel));
    }

    // Start blocks in correct order based on operator
    if (isOr) {
      // OR: short-circuit block first, then eval right
      builder.startBlock(shortCircuitLabel);
      builder.setTerminator(new IrTerminator.IrJmpTerm(mergeLabel));
      
      builder.startBlock(evalRightLabel);
      emitter.emitRValue(rightNode, functionContext);
      builder.setTerminator(new IrTerminator.IrJmpTerm(mergeLabel));
    } else {
      // AND: eval right block first, then short-circuit
      builder.startBlock(evalRightLabel);
      emitter.emitRValue(rightNode, functionContext);
      builder.setTerminator(new IrTerminator.IrJmpTerm(mergeLabel));
      
      builder.startBlock(shortCircuitLabel);
      builder.setTerminator(new IrTerminator.IrJmpTerm(mergeLabel));
    }

    // Merge block (continues execution)
    builder.startBlock(mergeLabel);
  }

  /**
   * Helper method to convert a value to bool (for truthiness).
   */
  private IrValue convertToBool(IrValue value, Type originalType, IrFunctionBuilder builder) {
    boolean isAlreadyBool =
        value instanceof IrTemp temp && temp.type().equals(IrPrimitiveType.BOOL);
    if (!isAlreadyBool && originalType != null && originalType.isScalar()) {
      IrType irType = TypeMapper.toIrType(originalType);
      IrConst zero;
      if (originalType == PrimitiveType.INT || originalType == PrimitiveType.CHAR) {
        zero = new IrConst.IntConst(0, irType);
      } else if (originalType == PrimitiveType.FLOAT) {
        zero = new IrConst.FloatConst(0.0f);
      } else {
        zero = new IrConst.NullConst(irType);
      }
      IrRhs.CmpOp cmpNe = new IrRhs.CmpOp(IrRhs.CmpOp.CmpOpName.NE, value, zero);
      IrTemp boolTemp = builder.tempFactory().newTemp(IrPrimitiveType.BOOL);
      builder.addInstruction(new IrInstruction.IrAssignInstr(boolTemp, cmpNe));
      return boolTemp;
    }
    return value;
  }
}
