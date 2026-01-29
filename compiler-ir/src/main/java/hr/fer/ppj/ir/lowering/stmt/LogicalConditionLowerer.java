package hr.fer.ppj.ir.lowering.stmt;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.ExpressionGenerator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.ExpressionNameExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Lowers logical conditions (AND/OR) for branching contexts.
 *
 * <p>This component handles short-circuit evaluation of logical expressions
 * in if conditions, ensuring the generated CFG matches golden IR patterns.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LogicalConditionLowerer {

  private final ExpressionGenerator expressionGenerator;

  public LogicalConditionLowerer(ExpressionGenerator expressionGenerator) {
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
  }

  /**
   * Emits a condition as a branch, handling logical expressions with short-circuit evaluation.
   *
   * @param expr the condition expression node
   * @param ctx the function context
   * @param trueLabel the label to jump to if condition is true
   * @param falseLabel the label to jump to if condition is false
   */
  public void emitConditionAsBranch(
      NonTerminalNode expr, FunctionContext ctx, String trueLabel, String falseLabel) {
    Objects.requireNonNull(expr, "expr must not be null");
    Objects.requireNonNull(ctx, "ctx must not be null");
    Objects.requireNonNull(trueLabel, "trueLabel must not be null");
    Objects.requireNonNull(falseLabel, "falseLabel must not be null");

    String symbol = expr.symbol();
    IrFunctionBuilder builder = ctx.functionBuilder();

    // Check if this is a logical OR expression
    if (symbol.equals("<log_ili_izraz>")) {
      List<ParseNode> children = expr.children();
      if (children.size() >= 3) {
        // Check if it has an operator (not just a wrapper)
        boolean hasOperator = false;
        for (ParseNode child : children) {
          if (child instanceof TerminalNode term && term.symbol().equals("OP_ILI")) {
            hasOperator = true;
            break;
          }
        }
        if (hasOperator) {
          // This is a real OR expression: A || B
          NonTerminalNode leftNode = NodeUtils.asNonTerminal(children.get(0), "<log_i_izraz>");
          NonTerminalNode rightNode = NodeUtils.asNonTerminal(children.get(2), "<izraz_pridruzivanja>");

          // Create label for evaluating right side
          String evalRightLabel = builder.labelFactory().newLabel();

          // Evaluate left side: if true, go to trueLabel; else, evaluate right side
          emitConditionAsBranch(leftNode, ctx, trueLabel, evalRightLabel);

          // Start block for evaluating right side
          builder.startBlock(evalRightLabel);

          // Evaluate right side as a condition and branch
          emitConditionAsBranch(rightNode, ctx, trueLabel, falseLabel);
          return;
        }
      }
    }

    // Check if this is a logical AND expression
    if (symbol.equals("<log_i_izraz>")) {
      List<ParseNode> children = expr.children();
      if (children.size() >= 3) {
        // Check if it has an operator (not just a wrapper)
        boolean hasOperator = false;
        for (ParseNode child : children) {
          if (child instanceof TerminalNode term && term.symbol().equals("OP_I")) {
            hasOperator = true;
            break;
          }
        }
        if (hasOperator) {
          // This is a real AND expression: A && B
          NonTerminalNode leftNode = NodeUtils.asNonTerminal(children.get(0), "<bin_ili_izraz>");
          NonTerminalNode rightNode = NodeUtils.asNonTerminal(children.get(2), "<izraz_pridruzivanja>");

          // Create label for evaluating right side
          String evalRightLabel = builder.labelFactory().newLabel();

          // Evaluate left side: if false, go to falseLabel; else, evaluate right side
          emitConditionAsBranch(leftNode, ctx, evalRightLabel, falseLabel);

          // Start block for evaluating right side
          builder.startBlock(evalRightLabel);

          // Evaluate right side as a condition and branch
          emitConditionAsBranch(rightNode, ctx, trueLabel, falseLabel);
          return;
        }
      }
    }

    // For non-logical expressions, evaluate and branch normally
    // Special handling for assignments: evaluate assignment, then load variable and compare to 0
    NonTerminalNode assignmentNode = findAssignmentInExpression(expr);
    if (assignmentNode != null && assignmentNode.symbol().equals("<izraz_pridruzivanja>")) {
      // This is an assignment - evaluate it, then load the variable and compare to 0
      List<ParseNode> assignChildren = assignmentNode.children();
      if (assignChildren.size() >= 3) {
        ParseNode leftAssignNode = assignChildren.get(0);
        NonTerminalNode leftUnary = NodeUtils.asNonTerminal(leftAssignNode, "<unarni_izraz>");
        String varName = ExpressionNameExtractor.extractVariableName(leftUnary);
        if (varName != null) {
          // Evaluate the assignment (emits store)
          expressionGenerator.emitRValue(assignmentNode, ctx);
          
          // Load the variable after assignment using emitLValue to get address
          // This handles symbol resolution properly (local/param/global)
          IrTemp varAddr = expressionGenerator.emitLValue(leftUnary, ctx);
          
          Type exprType = expr.attributes().type();
          hr.fer.ppj.ir.types.IrType irType = TypeMapper.toIrType(exprType);
          IrRhs.Load loadVar = new IrRhs.Load(varAddr, irType);
          IrTemp loadedValue = builder.tempFactory().newTemp(irType);
          builder.addInstruction(new IrInstruction.IrAssignInstr(loadedValue, loadVar));
          
          // Convert to bool and branch
          IrValue condition = ConditionConverter.convertToBool(loadedValue, exprType, builder);
          builder.setTerminator(new IrTerminator.IrBrTerm(condition, trueLabel, falseLabel));
          return;
        }
      }
    }
    
    // For non-assignment expressions, evaluate and branch normally
    IrValue condition = expressionGenerator.emitRValue(expr, ctx);
    Type condType = expr.attributes().type();
    condition = ConditionConverter.convertToBool(condition, condType, builder);
    builder.setTerminator(new IrTerminator.IrBrTerm(condition, trueLabel, falseLabel));
  }

  /**
   * Recursively finds an assignment expression node (<izraz_pridruzivanja>).
   */
  private NonTerminalNode findAssignmentInExpression(NonTerminalNode node) {
    String symbol = node.symbol();
    if (symbol.equals("<izraz_pridruzivanja>")) {
      // Check if this is an assignment (has OP_PRIDRUZI)
      List<ParseNode> children = node.children();
      for (ParseNode child : children) {
        if (child instanceof TerminalNode term && term.symbol().equals("OP_PRIDRUZI")) {
          return node;
        }
      }
    }
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode childNode) {
        NonTerminalNode found = findAssignmentInExpression(childNode);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
