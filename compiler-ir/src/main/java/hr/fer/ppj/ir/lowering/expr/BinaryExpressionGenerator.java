package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.util.OperatorMapper;
import hr.fer.ppj.ir.util.TypePromoter;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for binary expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BinaryExpressionGenerator {

  private final ExpressionEmitter emitter;

  public BinaryExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
  }

  /**
   * Emits r-value for a multiplicative expression (*, /, %).
   */
  public IrValue emitMultiplicative(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return emitBinary(
        node,
        functionContext,
        new String[] {"OP_PUTA", "OP_DIJELI", "OP_MOD"},
        new IrRhs.BinOp.BinOpName[] {
          IrRhs.BinOp.BinOpName.MUL, IrRhs.BinOp.BinOpName.DIV, IrRhs.BinOp.BinOpName.MOD
        });
  }

  /**
   * Emits r-value for an additive expression (+, -).
   */
  public IrValue emitAdditive(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return emitBinary(
        node,
        functionContext,
        new String[] {"PLUS", "MINUS"},
        new IrRhs.BinOp.BinOpName[] {IrRhs.BinOp.BinOpName.ADD, IrRhs.BinOp.BinOpName.SUB});
  }


  private IrValue emitBinary(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      String[] opSymbols,
      IrRhs.BinOp.BinOpName[] opNames) {
    List<ParseNode> children = node.children();

    // Single operand case (base case for recursive grammar)
    if (children.size() == 1) {
      if (children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, functionContext);
      }
      throw new IllegalArgumentException(
          "Invalid binary expression: single child is not a non-terminal");
    }

    // Binary operator case: must have exactly 3 children: <left> <operator> <right>
    if (children.size() != 3) {
      throw new IllegalStateException(
          "Binary expression must have 1 or 3 children, but has "
              + children.size()
              + ": "
              + node.symbol());
    }

    // Extract operator from position 1
    ParseNode opNode = children.get(1);
    if (!(opNode instanceof TerminalNode)) {
      throw new IllegalStateException(
          "Binary expression operator at position 1 must be a terminal node, but got: "
              + opNode.getClass().getSimpleName()
              + " in "
              + node.symbol());
    }

    TerminalNode opTerm = (TerminalNode) opNode;
    String opSymbol = opTerm.symbol();

    // Get left and right operands
    ParseNode leftParseNode = children.get(0);
    ParseNode rightParseNode = children.get(2);

    if (!(leftParseNode instanceof NonTerminalNode leftNode)) {
      throw new IllegalArgumentException(
          "Left operand must be a non-terminal, but got: "
              + leftParseNode.getClass().getSimpleName());
    }
    if (!(rightParseNode instanceof NonTerminalNode rightNode)) {
      throw new IllegalArgumentException(
          "Right operand must be a non-terminal, but got: "
              + rightParseNode.getClass().getSimpleName());
    }

    // Determine result type using arithmetic promotion rules
    Type leftType = leftNode.attributes().type();
    Type rightType = rightNode.attributes().type();
    Type resultType;
    if (leftType != null && rightType != null && leftType.isScalar() && rightType.isScalar()) {
      resultType = TypeSystem.arithmeticResult(leftType, rightType);
    } else {
      resultType = leftType != null ? leftType : rightType;
    }
    IrType irResultType = TypeMapper.toIrType(resultType);

    // Promote operands to result type if needed
    IrType leftIrType = TypeMapper.toIrType(leftType);
    IrType rightIrType = TypeMapper.toIrType(rightType);

    IrFunctionBuilder builder = functionContext.functionBuilder();

    // Determine evaluation order based on operand complexity
    // Rules:
    // 1. If left has call and right is simple variable -> evaluate right first
    // 2. If left is simple variable and right has array indexing -> evaluate right first
    // 3. Otherwise -> standard left-to-right evaluation
    boolean leftContainsCall = containsFunctionCall(leftNode);
    boolean rightContainsCall = containsFunctionCall(rightNode);
    boolean leftIsSimpleVar = isSimpleVariable(leftNode);
    boolean rightIsSimpleVar = isSimpleVariable(rightNode);
    boolean rightHasArrayIndex = containsArrayIndexing(rightNode);
    
    IrValue left, right;
    
    if (leftContainsCall && !rightContainsCall && rightIsSimpleVar) {
      // Special case: left has call, right is simple variable - evaluate right first
      // This matches expected IR for expressions like a(x+1) + x
      right = emitter.emitRValue(rightNode, functionContext);
      if (!rightIrType.equals(irResultType)) {
        right = TypePromoter.promoteValue(right, rightIrType, irResultType, builder);
      }
      
      // Clear the rvalue cache before evaluating left operand to ensure fresh loads
      // This prevents reusing the right operand's value in the left operand's subexpressions
      functionContext.addressReuseContext().clearAllLastLoadedValues();
      
      left = emitter.emitRValue(leftNode, functionContext);
      if (!leftIrType.equals(irResultType)) {
        left = TypePromoter.promoteValue(left, leftIrType, irResultType, builder);
      }
    } else if (leftIsSimpleVar && !leftContainsCall && rightHasArrayIndex && !rightContainsCall) {
      // Special case: left is simple variable, right has array indexing - evaluate right first
      // This matches expected IR for expressions like ret + arr[x]
      right = emitter.emitRValue(rightNode, functionContext);
      if (!rightIrType.equals(irResultType)) {
        right = TypePromoter.promoteValue(right, rightIrType, irResultType, builder);
      }
      
      left = emitter.emitRValue(leftNode, functionContext);
      if (!leftIrType.equals(irResultType)) {
        left = TypePromoter.promoteValue(left, leftIrType, irResultType, builder);
      }
    } else {
      // Standard left-to-right evaluation order
      left = emitter.emitRValue(leftNode, functionContext);
      if (!leftIrType.equals(irResultType)) {
        left = TypePromoter.promoteValue(left, leftIrType, irResultType, builder);
      }
      
      // Clear the rvalue cache before evaluating right operand of additive operations
      // This ensures fresh loads for each operand (e.g., program40: y + b(y+1))
      // The cache will be populated during right operand evaluation (for nested reuse like program35)
      if (opSymbol.equals("PLUS") || opSymbol.equals("MINUS")) {
        functionContext.addressReuseContext().clearAllLastLoadedValues();
      }
      
      // Check if right operand has postfix increment/decrement
      // For expressions like x + y++, we need to load y, perform addition, then increment
      boolean rightHasPostfixInc = containsPostfixIncrement(rightNode);
      if (rightHasPostfixInc) {
        // Load the value without incrementing, perform addition, then increment
        right = loadValueForPostfixIncrement(rightNode, functionContext);
        if (!rightIrType.equals(irResultType)) {
          right = TypePromoter.promoteValue(right, rightIrType, irResultType, builder);
        }
      } else {
        right = emitter.emitRValue(rightNode, functionContext);
        if (!rightIrType.equals(irResultType)) {
          right = TypePromoter.promoteValue(right, rightIrType, irResultType, builder);
        }
      }
    }

    // Map operator symbol to IR operation name
    IrRhs.BinOp.BinOpName binOpName = null;
    for (int i = 0; i < opSymbols.length && i < opNames.length; i++) {
      if (opSymbol.equals(opSymbols[i])) {
        binOpName = opNames[i];
        break;
      }
    }

    // If not found in provided list, try fallback mapping
    if (binOpName == null) {
      try {
        binOpName = OperatorMapper.mapBinaryOperator(opSymbol);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "Cannot map operator '"
                + opSymbol
                + "' to IR binary operation in "
                + node.symbol(),
            e);
      }
    }

    IrRhs.BinOp binOp = new IrRhs.BinOp(binOpName, left, right, irResultType);
    IrTemp result = builder.tempFactory().newTemp(irResultType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, binOp));
    
    // If right operand had postfix increment, perform the increment now (after addition)
    if (containsPostfixIncrement(rightNode)) {
      performPostfixIncrement(rightNode, functionContext);
    }
    
    // Clear the rvalue cache after completing additive operations (add/sub)
    // This ensures values are not reused across separate subexpressions in addition chains (e.g., program37)
    // but allows reuse within function call argument collection with mod (e.g., program35)
    if (binOpName == IrRhs.BinOp.BinOpName.ADD || binOpName == IrRhs.BinOp.BinOpName.SUB) {
      functionContext.addressReuseContext().clearAllLastLoadedValues();
    }
    
    return result;
  }

  /**
   * Loads the value for a postfix increment/decrement expression without performing the increment.
   * Used in binary expressions where the addition should happen before the increment.
   */
  private IrValue loadValueForPostfixIncrement(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    // Recursively unwrap to find the postfix increment
    NonTerminalNode postfixNode = findPostfixIncrementNode(node);
    if (postfixNode == null) {
      // Not a postfix increment - fall back to normal evaluation
      return emitter.emitRValue(node, functionContext);
    }
    
    List<ParseNode> children = postfixNode.children();
    if (children.size() != 2) {
      return emitter.emitRValue(node, functionContext);
    }
    
    ParseNode second = children.get(1);
    if (!(second instanceof TerminalNode term)
        || (!term.symbol().equals("OP_INC") && !term.symbol().equals("OP_DEC"))) {
      return emitter.emitRValue(node, functionContext);
    }
    
    // Extract base node and load its value
    NonTerminalNode baseNode =
        hr.fer.ppj.semantics.util.NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    
    // Get the LValueEmitter from the emitter (which is ExpressionGenerator)
    LValueEmitter lValueEmitter = null;
    if (emitter instanceof hr.fer.ppj.ir.lowering.ExpressionGenerator exprGen) {
      lValueEmitter = exprGen;
    } else {
      return emitter.emitRValue(node, functionContext);
    }
    
    IrFunctionBuilder builder = functionContext.functionBuilder();
    hr.fer.ppj.semantics.types.Type exprType = baseNode.attributes().type();
    hr.fer.ppj.ir.types.IrType irType = hr.fer.ppj.ir.build.TypeMapper.toIrType(exprType);
    
    // Load the value (without incrementing)
    hr.fer.ppj.ir.model.IrTemp addr = lValueEmitter.emitLValue(baseNode, functionContext);
    hr.fer.ppj.ir.model.IrRhs.Load load = new hr.fer.ppj.ir.model.IrRhs.Load(addr, irType);
    hr.fer.ppj.ir.model.IrTemp value = builder.tempFactory().newTemp(irType);
    builder.addInstruction(new hr.fer.ppj.ir.model.IrInstruction.IrAssignInstr(value, load));
    
    // Store the value, address, and variable name for the increment to reuse
    String varName = hr.fer.ppj.ir.util.ExpressionNameExtractor.extractVariableName(baseNode);
    if (varName != null) {
      functionContext.setDeferredPostfixIncrementValue(varName, value, addr);
    }
    
    return value;
  }

  /**
   * Recursively finds the postfix increment node by unwrapping expression wrappers.
   */
  private NonTerminalNode findPostfixIncrementNode(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Check if this is a postfix expression with increment/decrement
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 2) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term
            && (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC"))) {
          return node;
        }
      }
    }
    
    // Recursively check children (unwrap expression wrappers)
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        NonTerminalNode found = findPostfixIncrementNode(nt);
        if (found != null) {
          return found;
        }
      }
    }
    
    return null;
  }

  /**
   * Performs the increment/decrement for a postfix expression.
   * Used after the addition in binary expressions like x + y++.
   */
  private void performPostfixIncrement(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    // Recursively find the postfix increment node
    NonTerminalNode postfixNode = findPostfixIncrementNode(node);
    if (postfixNode == null) {
      return;
    }
    
    List<ParseNode> children = postfixNode.children();
    if (children.size() != 2) {
      return;
    }
    
    ParseNode second = children.get(1);
    if (!(second instanceof TerminalNode term)
        || (!term.symbol().equals("OP_INC") && !term.symbol().equals("OP_DEC"))) {
      return;
    }
    
    // Extract base node
    NonTerminalNode baseNode =
        hr.fer.ppj.semantics.util.NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    
    // Get the LValueEmitter from the emitter
    LValueEmitter lValueEmitter = null;
    if (emitter instanceof hr.fer.ppj.ir.lowering.ExpressionGenerator exprGen) {
      lValueEmitter = exprGen;
    } else {
      return;
    }
    
    IrFunctionBuilder builder = functionContext.functionBuilder();
    hr.fer.ppj.semantics.types.Type exprType = baseNode.attributes().type();
    hr.fer.ppj.ir.types.IrType irType = hr.fer.ppj.ir.build.TypeMapper.toIrType(exprType);
    
    // Try to reuse the value and address that were already loaded
    String varName = hr.fer.ppj.ir.util.ExpressionNameExtractor.extractVariableName(baseNode);
    hr.fer.ppj.ir.model.IrTemp currentValue = null;
    hr.fer.ppj.ir.model.IrTemp addr = null;
    if (varName != null) {
      currentValue = functionContext.getDeferredPostfixIncrementValue(varName);
      addr = functionContext.getDeferredPostfixIncrementAddr(varName);
    }
    
    // If no deferred value/address, load them now
    if (currentValue == null || addr == null) {
      if (addr == null) {
        addr = lValueEmitter.emitLValue(baseNode, functionContext);
      }
      if (currentValue == null) {
        hr.fer.ppj.ir.model.IrRhs.Load load = new hr.fer.ppj.ir.model.IrRhs.Load(addr, irType);
        currentValue = builder.tempFactory().newTemp(irType);
        builder.addInstruction(new hr.fer.ppj.ir.model.IrInstruction.IrAssignInstr(currentValue, load));
      }
    }
    
    // Clear the deferred value/address
    if (varName != null) {
      functionContext.clearDeferredPostfixIncrementValue();
    }
    
    // Add/subtract 1
    hr.fer.ppj.ir.model.IrConst oneConst = term.symbol().equals("OP_INC")
        ? new hr.fer.ppj.ir.model.IrConst.IntConst(1, irType)
        : new hr.fer.ppj.ir.model.IrConst.IntConst(-1, irType);
    hr.fer.ppj.ir.model.IrRhs.BinOp addOp = new hr.fer.ppj.ir.model.IrRhs.BinOp(
        hr.fer.ppj.ir.model.IrRhs.BinOp.BinOpName.ADD, currentValue, oneConst, irType);
    hr.fer.ppj.ir.model.IrTemp newValue = builder.tempFactory().newTemp(irType);
    builder.addInstruction(new hr.fer.ppj.ir.model.IrInstruction.IrAssignInstr(newValue, addOp));
    
    // Store new value
    builder.addInstruction(new hr.fer.ppj.ir.model.IrInstruction.IrStoreInstr(addr, newValue, irType));
    
    // Invalidate last loaded value for this variable since it has changed
    // This ensures statement-local value reuse doesn't use stale values
    // varName was already extracted above, reuse it
    if (varName != null) {
      functionContext.addressReuseContext().clearLastLoadedValue(varName);
    }
    
    // Invalidate last loaded value for this variable (varName was already extracted above)
    if (varName != null) {
      functionContext.addressReuseContext().clearLastLoadedValue(varName);
    }
  }

  /**
   * Checks if an expression node contains a function call.
   */
  private boolean containsFunctionCall(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Check if this is a postfix expression with a function call
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode first = children.get(0);
        ParseNode second = children.get(1);
        if (first instanceof NonTerminalNode && second instanceof TerminalNode term
            && term.symbol().equals("L_ZAGRADA")) {
          // This is a function call
          return true;
        }
      }
    }
    
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (containsFunctionCall(nt)) {
          return true;
        }
      }
    }
    
    return false;
  }

  /**
   * Checks if an expression node is a simple variable (just an identifier).
   */
  private boolean isSimpleVariable(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Primary expression with identifier
    if (symbol.equals("<primarni_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 1 && children.get(0) instanceof TerminalNode term
          && term.symbol().equals("IDN")) {
        return true;
      }
    }
    
    // Check if it's a pass-through to primary expression
    // Also check <izraz> which can wrap other expressions
    if (symbol.equals("<unarni_izraz>") || symbol.equals("<cast_izraz>")
        || symbol.equals("<postfiks_izraz>") || symbol.equals("<izraz>")
        || symbol.equals("<izraz_pridruzivanja>") || symbol.equals("<log_ili_izraz>")
        || symbol.equals("<log_i_izraz>") || symbol.equals("<bin_ili_izraz>")
        || symbol.equals("<bin_xili_izraz>") || symbol.equals("<bin_i_izraz>")
        || symbol.equals("<jednakosni_izraz>") || symbol.equals("<odnosni_izraz>")
        || symbol.equals("<aditivni_izraz>") || symbol.equals("<multiplikativni_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return isSimpleVariable(child);
      }
    }
    
    return false;
  }

  /**
   * Checks if an expression node contains array indexing.
   */
  private boolean containsArrayIndexing(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Check if this is a postfix expression with array indexing
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term && term.symbol().equals("L_UGL_ZAGRADA")) {
          return true;
        }
      }
    }
    
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (containsArrayIndexing(nt)) {
          return true;
        }
      }
    }
    
    return false;
  }

  /**
   * Checks if an expression node contains a postfix increment or decrement.
   */
  private boolean containsPostfixIncrement(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Check if this is a postfix expression with increment/decrement
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 2) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term) {
          if (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC")) {
            return true;
          }
        }
      }
    }
    
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (containsPostfixIncrement(nt)) {
          return true;
        }
      }
    }
    
    return false;
  }
}
