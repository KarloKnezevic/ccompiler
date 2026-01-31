package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.AddressReuseContext;
import hr.fer.ppj.ir.util.ExpressionNameExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for assignment expressions.
 *
 * <p>Handles the {@code <izraz_pridruzivanja>} grammar rule:
 * <pre>
 * &lt;izraz_pridruzivanja&gt; ::= &lt;log_ili_izraz&gt;
 *                        | &lt;unarni_izraz&gt; OP_PRIDRUZI &lt;izraz_pridruzivanja&gt;
 * </pre>
 *
 * <p>Generates store instructions as defined in the IR grammar:
 * <pre>
 * StoreInstr ::= "store" Value "," Value ":" Type ;
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class AssignmentExpressionGenerator {

  private final ExpressionEmitter emitter;
  private final LValueEmitter lValueEmitter;
  private final ArrayIndexAddressHelper arrayHelper;

  /**
   * Creates a new assignment expression generator.
   */
  public AssignmentExpressionGenerator(ExpressionEmitter emitter, LValueEmitter lValueEmitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.lValueEmitter = Objects.requireNonNull(lValueEmitter, "lValueEmitter must not be null");
    this.arrayHelper = new ArrayIndexAddressHelper(emitter, lValueEmitter);
  }

  /**
   * Emits r-value for an assignment expression.
   *
   * @param node the assignment expression node
   * @param ctx the function context
   * @return the assigned value
   */
  public IrValue emitRValue(NonTerminalNode node, FunctionContext ctx) {
    List<ParseNode> children = node.children();
    IrFunctionBuilder builder = ctx.functionBuilder();

    if (children.size() >= 3 && isAssignment(children)) {
      return emitAssignment(children, ctx, builder);
    }

    // Pass-through to child expression
    if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
      return emitter.emitRValue(nt, ctx);
    }

    throw new IllegalArgumentException("Cannot emit r-value for assignment expression");
  }

  private boolean isAssignment(List<ParseNode> children) {
    return children.get(1) instanceof TerminalNode t && t.symbol().equals("OP_PRIDRUZI");
  }

  private IrValue emitAssignment(
      List<ParseNode> children, FunctionContext ctx, IrFunctionBuilder builder) {

    NonTerminalNode leftNode = NodeUtils.asNonTerminal(children.get(0), "<unarni_izraz>");
    NonTerminalNode rightNode = NodeUtils.asNonTerminal(children.get(2), "<izraz_pridruzivanja>");

    AddressReuseContext reuseCtx = ctx.addressReuseContext();
    String leftVarName = ExpressionNameExtractor.extractVariableName(leftNode);
    boolean shouldReuse = AssignmentOrderAnalyzer.shouldReuseAddress(leftNode, rightNode);

    // Determine evaluation order based on expression patterns
    EvaluationResult result = determineEvaluationOrder(leftNode, rightNode, shouldReuse, ctx);

    Type leftType = leftNode.attributes().type();
    IrType irType = TypeMapper.toIrType(leftType);
    IrValue value = handleNullPointerAssignment(result.value, irType);

    builder.addInstruction(new IrInstruction.IrStoreInstr(result.addr, value, irType));

    // Invalidate cached value for modified variable
    if (leftVarName != null) {
      reuseCtx.clearLastLoadedValue(leftVarName);
    }

    return value;
  }

  private EvaluationResult determineEvaluationOrder(
      NonTerminalNode left, NonTerminalNode right,
      boolean shouldReuse, FunctionContext ctx) {

    AddressReuseContext reuseCtx = ctx.addressReuseContext();
    String leftVarName = ExpressionNameExtractor.extractVariableName(left);
    IrTemp savedAddr = reuseCtx.assignmentReuseAddr();
    String savedVarName = reuseCtx.assignmentReuseVarName();

    boolean rightHasCast = AssignmentOrderAnalyzer.containsCast(right);
    boolean leftIsArrayIndex = AssignmentOrderAnalyzer.isArrayIndexing(left);
    boolean rightHasArrayIndex = AssignmentOrderAnalyzer.containsArrayIndexing(right);
    boolean rightHasPreInc = AssignmentOrderAnalyzer.containsPreIncrement(right);
    boolean leftIsSimpleVar = AssignmentOrderAnalyzer.isSimpleVariable(left);

    // Set up reuse context for array indexing in right side
    if (shouldReuse && rightHasArrayIndex && leftIsSimpleVar) {
      reuseCtx.setAssignmentReuseVarName(leftVarName);
    }

    // Choose evaluation strategy based on expression patterns
    if (rightHasCast && leftIsArrayIndex) {
      return evalCastWithArrayIndex(left, right, ctx);
    }
    if (rightHasCast) {
      return evalRightFirst(left, right, shouldReuse, ctx);
    }
    if (rightHasPreInc && leftIsSimpleVar) {
      return evalRightFirst(left, right, false, ctx);
    }
    if (leftIsSimpleVar) {
      return evalSimpleVariable(left, right, shouldReuse, savedAddr, savedVarName, ctx);
    }
    return evalStandardOrder(left, right, shouldReuse, savedAddr, savedVarName, ctx);
  }

  private EvaluationResult evalCastWithArrayIndex(
      NonTerminalNode left, NonTerminalNode right, FunctionContext ctx) {
    IrTemp baseAddr = arrayHelper.getArrayBaseAddress(left, ctx);
    IrTemp addr = arrayHelper.computeArrayIndexAddress(left, baseAddr, ctx);
    IrValue value = emitter.emitRValue(right, ctx);
    return new EvaluationResult(addr, value);
  }

  private EvaluationResult evalRightFirst(
      NonTerminalNode left, NonTerminalNode right,
      boolean shouldReuse, FunctionContext ctx) {
    IrValue value = emitter.emitRValue(right, ctx);
    IrTemp addr = lValueEmitter.emitLValue(left, ctx);
    if (shouldReuse) {
      String varName = ExpressionNameExtractor.extractVariableName(left);
      ctx.addressReuseContext().setAssignmentReuse(addr, varName);
    }
    return new EvaluationResult(addr, value);
  }

  private EvaluationResult evalSimpleVariable(
      NonTerminalNode left, NonTerminalNode right, boolean shouldReuse,
      IrTemp savedAddr, String savedVarName, FunctionContext ctx) {

    Type leftType = TypeSystem.stripConst(left.attributes().type());
    if (leftType instanceof StructType) {
      // Struct: standard order
      IrTemp addr = lValueEmitter.emitLValue(left, ctx);
      IrValue value = emitter.emitRValue(right, ctx);
      return new EvaluationResult(addr, value);
    }

    // Scalar: r-value first
    IrValue value = emitter.emitRValue(right, ctx);
    AddressReuseContext reuseCtx = ctx.addressReuseContext();
    String leftVarName = ExpressionNameExtractor.extractVariableName(left);
    IrTemp addr;

    if (shouldReuse) {
      IrTemp lastLoadAddr = reuseCtx.getLastLoadAddress(leftVarName);
      if (lastLoadAddr != null) {
        addr = lastLoadAddr;
        reuseCtx.clearLastLoadAddress();
        reuseCtx.setAssignmentReuse(savedAddr, savedVarName);
      } else {
        addr = lValueEmitter.emitLValue(left, ctx);
        reuseCtx.setAssignmentReuse(savedAddr, savedVarName);
      }
    } else {
      addr = lValueEmitter.emitLValue(left, ctx);
    }
    return new EvaluationResult(addr, value);
  }

  private EvaluationResult evalStandardOrder(
      NonTerminalNode left, NonTerminalNode right, boolean shouldReuse,
      IrTemp savedAddr, String savedVarName, FunctionContext ctx) {

    AddressReuseContext reuseCtx = ctx.addressReuseContext();
    String leftVarName = ExpressionNameExtractor.extractVariableName(left);
    IrTemp addr = lValueEmitter.emitLValue(left, ctx);

    if (shouldReuse) {
      reuseCtx.setAssignmentReuse(addr, leftVarName);
    }

    IrValue value;
    try {
      value = emitter.emitRValue(right, ctx);
    } finally {
      reuseCtx.setAssignmentReuse(savedAddr, savedVarName);
    }
    return new EvaluationResult(addr, value);
  }

  private IrValue handleNullPointerAssignment(IrValue value, IrType targetType) {
    if (targetType instanceof IrPointerType ptrType
        && value instanceof IrConst.IntConst ic && ic.value() == 0) {
      return new IrConst.NullConst(ptrType);
    }
    return value;
  }

  private record EvaluationResult(IrTemp addr, IrValue value) {}
}
