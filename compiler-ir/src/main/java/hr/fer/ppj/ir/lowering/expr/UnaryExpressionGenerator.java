package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.util.ConstantEvaluator;
import hr.fer.ppj.ir.util.ExpressionNameExtractor;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for unary expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class UnaryExpressionGenerator {

  private final ExpressionEmitter emitter;
  private final CastExpressionGenerator castGenerator;
  private final LValueEmitter lValueEmitter;

  public UnaryExpressionGenerator(
      ExpressionEmitter emitter,
      CastExpressionGenerator castGenerator,
      LValueEmitter lValueEmitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.castGenerator = Objects.requireNonNull(castGenerator, "castGenerator must not be null");
    this.lValueEmitter = Objects.requireNonNull(lValueEmitter, "lValueEmitter must not be null");
  }

  /**
   * Emits r-value for a unary expression.
   *
   * <p><unarni_izraz> can be:
   * <ul>
   *   <li><cast_izraz> (no unary operator, just pass through)</li>
   *   <li>ASTERISK <cast_izraz> (dereference)</li>
   *   <li>OP_INC <cast_izraz> (pre-increment)</li>
   *   <li>OP_DEC <cast_izraz> (pre-decrement)</li>
   *   <li>PLUS <cast_izraz> (positive - identity)</li>
   *   <li>MINUS <cast_izraz> (negation)</li>
   *   <li>OP_TILDA <cast_izraz> (bitwise not)</li>
   *   <li>OP_NEG <cast_izraz> (logical not)</li>
   *   <li>AMPERSAND <cast_izraz> (address-of)</li>
   * </ul>
   */
  public IrValue emitRValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();

    // If there's no unary operator, just pass through to cast expression
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode nt) {
      String childSymbol = nt.symbol();
      if (childSymbol.equals("<cast_izraz>")) {
        return castGenerator.emitRValue(nt, functionContext);
      } else {
        return emitter.emitRValue(nt, functionContext);
      }
    }

    // Must have at least 2 children for unary operator (operator + operand)
    if (children.size() < 2) {
      if (children.isEmpty()) {
        throw new IllegalArgumentException("Empty unary expression");
      }
      ParseNode child = children.get(0);
      if (child instanceof TerminalNode) {
        throw new IllegalArgumentException(
            "Unary expression has terminal as single child: " + ((TerminalNode) child).symbol());
      }
      throw new IllegalArgumentException(
          "Invalid unary expression structure: "
              + children.size()
              + " children, first child type: "
              + child.getClass().getSimpleName());
    }

    ParseNode firstChild = children.get(0);

    // Check if first child is <unarni_operator> (non-terminal) or a terminal operator
    String op = null;
    NonTerminalNode operandNode = null;

    if (firstChild instanceof NonTerminalNode nt && nt.symbol().equals("<unarni_operator>")) {
      List<ParseNode> opChildren = nt.children();
      if (!opChildren.isEmpty() && opChildren.get(0) instanceof TerminalNode opTerm) {
        op = opTerm.symbol();
      }
      if (children.size() >= 2 && children.get(1) instanceof NonTerminalNode operandChild) {
        operandNode = operandChild;
      }
    } else if (firstChild instanceof TerminalNode term) {
      op = term.symbol();
      if (children.size() >= 2 && children.get(1) instanceof NonTerminalNode operandChild) {
        operandNode = operandChild;
      }
    }

    if (op != null && operandNode != null) {
      IrFunctionBuilder builder = functionContext.functionBuilder();

      if (op.equals("OP_INC") || op.equals("OP_DEC")) {
        // Generate explicit load/add/store sequence to match expected IR output format
        IrTemp addr = lValueEmitter.emitLValue(operandNode, functionContext);
        Type opType = operandNode.attributes().type();
        IrType irType = TypeMapper.toIrType(opType);

        // Load current value
        IrRhs.Load load = new IrRhs.Load(addr, irType);
        IrTemp currentValue = builder.tempFactory().newTemp(irType);
        builder.addInstruction(new IrInstruction.IrAssignInstr(currentValue, load));

        // Add/subtract 1
        IrConst oneConst = op.equals("OP_INC")
            ? new IrConst.IntConst(1, irType)
            : new IrConst.IntConst(-1, irType);
        IrRhs.BinOp addOp = new IrRhs.BinOp(
            IrRhs.BinOp.BinOpName.ADD, currentValue, oneConst, irType);
        IrTemp newValue = builder.tempFactory().newTemp(irType);
        builder.addInstruction(new IrInstruction.IrAssignInstr(newValue, addOp));

        // Store new value
        builder.addInstruction(new IrInstruction.IrStoreInstr(addr, newValue, irType));
        
        // Invalidate last loaded value for this variable since it has changed
        String varName = ExpressionNameExtractor.extractVariableName(operandNode);
        if (varName != null) {
          hr.fer.ppj.ir.util.AddressReuseContext addressReuseContext =
              functionContext.addressReuseContext();
          addressReuseContext.clearLastLoadedValue(varName);
        }

        // Return new value (pre-increment/decrement returns the new value)
        return newValue;
      } else if (op.equals("PLUS")) {
        // Unary plus - just return the operand value
        return emitter.emitRValue(operandNode, functionContext);
      } else if (op.equals("MINUS")) {
        return handleNegation(operandNode, functionContext, builder);
      } else if (op.equals("OP_TILDA")) {
        return handleBitwiseNot(operandNode, functionContext, builder);
      } else if (op.equals("OP_NEG")) {
        return handleLogicalNot(operandNode, functionContext, builder);
      } else if (op.equals("ASTERISK")) {
        return handleDereference(operandNode, functionContext, builder);
      } else if (op.equals("AMPERSAND")) {
        return lValueEmitter.emitLValue(operandNode, functionContext);
      }
    }

    // Fall through to cast expression
    if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
      return emitter.emitRValue(nt, functionContext);
    }

    throw new IllegalArgumentException("Cannot emit r-value for unary expression");
  }

  private IrValue handleNegation(
      NonTerminalNode operandNode,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      IrFunctionBuilder builder) {
    Type opType = operandNode.attributes().type();
    IrType irType = TypeMapper.toIrType(opType);
    
    // For floats, always emit neg instruction (golden tests expect: neg #1.5:float)
    // For integers, fold constant negation ONLY for direct literals (not compound expressions)
    if (irType == IrPrimitiveType.FLOAT) {
      IrValue operand = emitter.emitRValue(operandNode, functionContext);
      IrRhs.UnaryOp neg = new IrRhs.UnaryOp(IrRhs.UnaryOp.UnaryOpName.NEG, operand, irType);
      IrTemp result = builder.tempFactory().newTemp(irType);
      builder.addInstruction(new IrInstruction.IrAssignInstr(result, neg));
      return result;
    }
    
    // For non-float types, try to fold constant negation ONLY for direct literals
    if (opType != null && isDirectNumericLiteral(operandNode)) {
      try {
        IrConst negConst = ConstantEvaluator.extractConstantFromExpression(operandNode, opType);
        if (negConst != null && negConst instanceof IrConst.IntConst intConst) {
          return new IrConst.IntConst(-intConst.value(), intConst.type());
        }
      } catch (UnsupportedOperationException | IllegalArgumentException e) {
        // Not a constant - fall through to use neg instruction
      }
    }

    // Not a simple constant - use neg instruction
    IrValue operand = emitter.emitRValue(operandNode, functionContext);
    IrRhs.UnaryOp neg = new IrRhs.UnaryOp(IrRhs.UnaryOp.UnaryOpName.NEG, operand, irType);
    IrTemp result = builder.tempFactory().newTemp(irType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, neg));
    return result;
  }

  /**
   * Checks if the expression is a direct path to a numeric literal (BROJ).
   * This excludes compound expressions like (3 * 4).
   */
  private boolean isDirectNumericLiteral(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    // Follow single-child non-terminal chains
    while (children.size() == 1) {
      ParseNode child = children.get(0);
      if (child instanceof TerminalNode term) {
        // We've reached a terminal - check if it's a numeric literal
        return term.symbol().equals("BROJ");
      } else if (child instanceof NonTerminalNode nt) {
        // Continue following the chain
        children = nt.children();
      } else {
        return false;
      }
    }
    
    // Multiple children means a compound expression (e.g., binary op, parentheses with op inside)
    // Exception: parenthesized expression with just a literal inside
    if (children.size() == 3) {
      ParseNode first = children.get(0);
      ParseNode middle = children.get(1);
      ParseNode last = children.get(2);
      if (first instanceof TerminalNode t1 && t1.symbol().equals("L_ZAGRADA")
          && last instanceof TerminalNode t2 && t2.symbol().equals("D_ZAGRADA")
          && middle instanceof NonTerminalNode nt) {
        // Parenthesized expression - check if the inner expression is a direct literal
        return isDirectNumericLiteral(nt);
      }
    }
    
    return false;
  }

  private IrValue handleLogicalNot(
      NonTerminalNode operandNode,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      IrFunctionBuilder builder) {
    IrValue operand = emitter.emitRValue(operandNode, functionContext);
    IrRhs.UnaryOp not =
        new IrRhs.UnaryOp(IrRhs.UnaryOp.UnaryOpName.NOT, operand, IrPrimitiveType.BOOL);
    IrTemp result = builder.tempFactory().newTemp(IrPrimitiveType.BOOL);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, not));
    return result;
  }

  private IrValue handleBitwiseNot(
      NonTerminalNode operandNode,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      IrFunctionBuilder builder) {
    IrValue operand = emitter.emitRValue(operandNode, functionContext);
    Type opType = operandNode.attributes().type();
    IrType irType = TypeMapper.toIrType(opType);
    IrConst allBitsSet = new IrConst.IntConst(-1, irType);
    IrRhs.BinOp bitnot =
        new IrRhs.BinOp(IrRhs.BinOp.BinOpName.XOR, operand, allBitsSet, irType);
    IrTemp result = builder.tempFactory().newTemp(irType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, bitnot));
    return result;
  }

  private IrValue handleDereference(
      NonTerminalNode operandNode,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      IrFunctionBuilder builder) {
    IrValue ptr = emitter.emitRValue(operandNode, functionContext);
    Type ptrType = operandNode.attributes().type();
    if (ptrType instanceof PointerType pt) {
      IrType baseType = TypeMapper.toIrType(pt.baseType());
      IrRhs.Load load = new IrRhs.Load(ptr, baseType);
      IrTemp result = builder.tempFactory().newTemp(baseType);
      builder.addInstruction(new IrInstruction.IrAssignInstr(result, load));
      return result;
    }
    throw new IllegalArgumentException("Dereference operand must be a pointer type");
  }
}
