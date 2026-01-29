package hr.fer.ppj.ir.lowering;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.lowering.expr.BinaryExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.BitwiseExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.CastExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.AssignmentExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.ComparisonExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.ExpressionEmitter;
import hr.fer.ppj.ir.lowering.expr.LogicalExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.LValueEmitter;
import hr.fer.ppj.ir.lowering.expr.LValueGenerator;
import hr.fer.ppj.ir.lowering.expr.PostfixExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.PrimaryExpressionGenerator;
import hr.fer.ppj.ir.lowering.expr.UnaryExpressionGenerator;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for expressions.
 *
 * <p>This class routes expression generation to specialized generators following SRP.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ExpressionGenerator implements ExpressionEmitter, LValueEmitter {

  private final PrimaryExpressionGenerator primaryGenerator;
  private final CastExpressionGenerator castGenerator;
  private final UnaryExpressionGenerator unaryGenerator;
  private final BinaryExpressionGenerator binaryGenerator;
  private final BitwiseExpressionGenerator bitwiseGenerator;
  private final ComparisonExpressionGenerator comparisonGenerator;
  private final PostfixExpressionGenerator postfixGenerator;
  private final LogicalExpressionGenerator logicalGenerator;
  private final AssignmentExpressionGenerator assignmentGenerator;
  private final SymbolTable globalScope;
  private final hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry;
  private LValueGenerator lValueGenerator; // Lazy initialization to avoid circular dependency

  public ExpressionGenerator(SymbolTable globalScope, hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry) {
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.structNameRegistry = Objects.requireNonNull(structNameRegistry, "structNameRegistry must not be null");
    // Initialize generators with circular dependency via ExpressionEmitter interface
    this.primaryGenerator = new PrimaryExpressionGenerator(this);
    this.castGenerator = new CastExpressionGenerator(this);
    this.unaryGenerator = new UnaryExpressionGenerator(this, castGenerator, this);
    this.binaryGenerator = new BinaryExpressionGenerator(this);
    this.bitwiseGenerator = new BitwiseExpressionGenerator(this);
    this.comparisonGenerator = new ComparisonExpressionGenerator(this);
    this.postfixGenerator = new PostfixExpressionGenerator(this, this);
    this.logicalGenerator = new LogicalExpressionGenerator(this);
    this.assignmentGenerator = new AssignmentExpressionGenerator(this, this);
  }

  /**
   * Emits an l-value (address) for an expression.
   *
   * @param node the expression node
   * @param functionContext the function context
   * @return a temporary containing the address
   */
  public IrTemp emitLValue(NonTerminalNode node, FunctionContext functionContext) {
    Objects.requireNonNull(node, "node must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    if (lValueGenerator == null) {
      lValueGenerator = new LValueGenerator(
          this,
          globalScope,
          functionContext.variableNameManager(),
          functionContext.addressReuseContext(),
          structNameRegistry);
    }
    return lValueGenerator.emitLValue(node, functionContext);
  }

  /**
   * Emits an r-value (value) for an expression.
   *
   * @param node the expression node
   * @param functionContext the function context
   * @return a temporary or constant containing the value
   */
  @Override
  public IrValue emitRValue(NonTerminalNode node, FunctionContext functionContext) {
    Objects.requireNonNull(node, "node must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    // Ensure we have an active block if we're in a function context
    IrFunctionBuilder builder = functionContext.functionBuilder();
    if (builder != null && builder.getCurrentBlockLabel() == null) {
      builder.startNewBlock();
    }

    String symbol = node.symbol();

    return switch (symbol) {
      case "<izraz>" -> emitRValueExpression(node, functionContext);
      case "<primarni_izraz>" -> primaryGenerator.emitRValue(node, functionContext);
      case "<postfiks_izraz>" -> postfixGenerator.emitRValue(node, functionContext);
      case "<cast_izraz>" -> castGenerator.emitRValue(node, functionContext);
      case "<unarni_izraz>" -> unaryGenerator.emitRValue(node, functionContext);
      case "<multiplikativni_izraz>" -> binaryGenerator.emitMultiplicative(node, functionContext);
      case "<aditivni_izraz>" -> binaryGenerator.emitAdditive(node, functionContext);
      case "<odnosni_izraz>" -> comparisonGenerator.emitRelational(node, functionContext);
      case "<jednakosni_izraz>" -> comparisonGenerator.emitEquality(node, functionContext);
      case "<bin_i_izraz>" -> bitwiseGenerator.emitBinaryAnd(node, functionContext);
      case "<bin_xili_izraz>" -> bitwiseGenerator.emitBinaryXor(node, functionContext);
      case "<bin_ili_izraz>" -> bitwiseGenerator.emitBinaryOr(node, functionContext);
      case "<log_i_izraz>", "<log_ili_izraz>" ->
          logicalGenerator.emitRValueLogical(node, functionContext, true);
      case "<izraz_pridruzivanja>" ->
          assignmentGenerator.emitRValue(node, functionContext);
      default -> throw new UnsupportedOperationException(
          "R-value generation for " + symbol + " not yet extracted from IrGenerator");
    };
  }

  /**
   * Emits r-value for an <izraz> (expression).
   *
   * <p><izraz> can be:
   * <ul>
   *   <li><izraz_pridruzivanja> (single assignment expression)</li>
   *   <li><izraz> ZAREZ <izraz_pridruzivanja> (comma expression)</li>
   * </ul>
   *
   * <p>For comma expressions, we evaluate all expressions but return the value of the last one.
   */
  private IrValue emitRValueExpression(NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();

    // If it's a comma expression: <izraz> ZAREZ <izraz_pridruzivanja>
    // We need to evaluate all expressions but return the last one
    if (children.size() == 3) {
      // Evaluate left side (discard result)
      ParseNode left = children.get(0);
      if (left instanceof NonTerminalNode nt) {
        emitRValue(nt, functionContext);
      }
      // Return right side (last expression)
      ParseNode right = children.get(2);
      if (right instanceof NonTerminalNode nt) {
        return emitRValue(nt, functionContext);
      }
    }

    // Single <izraz_pridruzivanja> - just delegate
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode nt) {
      return emitRValue(nt, functionContext);
    }

    throw new IllegalArgumentException(
        "Invalid <izraz> structure: " + children.size() + " children");
  }
}
