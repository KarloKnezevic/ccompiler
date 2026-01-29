package hr.fer.ppj.ir.lowering.stmt;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.lowering.ExpressionGenerator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.lowering.LoopContext;
import hr.fer.ppj.ir.lowering.StatementGenerator;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for loop statements (while, for, do-while).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LoopStatementGenerator {

  private final ExpressionGenerator expressionGenerator;
  private final StatementGenerator statementGenerator;

  public LoopStatementGenerator(
      ExpressionGenerator expressionGenerator, StatementGenerator statementGenerator) {
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.statementGenerator =
        Objects.requireNonNull(statementGenerator, "statementGenerator must not be null");
  }

  /**
   * Generates a loop statement (while, for, or do-while).
   */
  public void generateLoopStatement(
      NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      return;
    }

    ParseNode firstChild = children.get(0);
    if (firstChild instanceof TerminalNode term) {
      String keyword = term.symbol();
      if (keyword.equals("KR_WHILE")) {
        generateWhileLoop(node, functionContext);
      } else if (keyword.equals("KR_FOR")) {
        generateForLoop(node, functionContext);
      } else if (keyword.equals("KR_DO")) {
        generateDoWhileLoop(node, functionContext);
      }
    }
  }

  private void generateWhileLoop(
      NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    IrFunctionBuilder builder = functionContext.functionBuilder();

    // Canonical while loop CFG: entry -> condLabel -> (bodyLabel -> condLabel, afterLabel)
    String condLabel = builder.labelFactory().newLabel();
    String bodyLabel = builder.labelFactory().newLabel();
    String afterLabel = builder.labelFactory().newLabel();

    LoopContext previousLoopContext = functionContext.loopContext();
    functionContext.setLoopContext(new LoopContext(afterLabel, condLabel));

    try {
      // Jump from entry to condition
      builder.setTerminator(new IrTerminator.IrJmpTerm(condLabel));

      // Condition block
      builder.startBlock(condLabel);
      NonTerminalNode conditionNode = null;
      for (ParseNode child : children) {
        if (child instanceof NonTerminalNode nt && nt.symbol().equals("<izraz>")) {
          conditionNode = nt;
          break;
        }
      }
      if (conditionNode != null) {
        IrValue condition = expressionGenerator.emitRValue(conditionNode, functionContext);
        Type condType = conditionNode.attributes().type();
        condition = ConditionConverter.convertToBool(condition, condType, builder);
        builder.setTerminator(new IrTerminator.IrBrTerm(condition, bodyLabel, afterLabel));
      } else {
        // No condition - always true, jump to body
        builder.setTerminator(new IrTerminator.IrJmpTerm(bodyLabel));
      }

      // Body block
      builder.startBlock(bodyLabel);
      for (ParseNode child : children) {
        if (child instanceof NonTerminalNode nt && nt.symbol().equals("<naredba>")) {
          statementGenerator.generateStatement(nt, functionContext);
          break;
        }
      }
      // Only add back-edge jump if body doesn't already have a terminator
      if (!currentBlockHasTerminator(builder)) {
        builder.setTerminator(new IrTerminator.IrJmpTerm(condLabel));
      }

      // After block (exit)
      builder.startBlock(afterLabel);
    } finally {
      functionContext.setLoopContext(previousLoopContext);
    }
  }

  private void generateForLoop(NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.size() < 6) {
      return;
    }

    IrFunctionBuilder builder = functionContext.functionBuilder();

    // Canonical for loop CFG: init -> condLabel -> (bodyLabel -> stepLabel -> condLabel, afterLabel)
    String condLabel = builder.labelFactory().newLabel();
    String bodyLabel = builder.labelFactory().newLabel();
    String afterLabel = builder.labelFactory().newLabel();
    // stepLabel will be created only if needed (when body terminates and continue is possible)
    String stepLabel = null;

    LoopContext previousLoopContext = functionContext.loopContext();
    // For continue, we'll set stepLabel later if needed
    functionContext.setLoopContext(new LoopContext(afterLabel, null));

    try {
      // Find init, cond, step, body nodes by searching for nonterminal symbols
      NonTerminalNode initNode = null;
      NonTerminalNode condNode = null;
      NonTerminalNode stepNode = null;
      NonTerminalNode bodyNode = null;

      for (ParseNode child : children) {
        if (child instanceof NonTerminalNode nt) {
          String symbol = nt.symbol();
          if (symbol.equals("<izraz_naredba>") && initNode == null) {
            initNode = nt;
          } else if (symbol.equals("<izraz_naredba>") && condNode == null && initNode != null) {
            condNode = nt;
          } else if (symbol.equals("<izraz>") && stepNode == null && condNode != null) {
            stepNode = nt;
          } else if (symbol.equals("<naredba>") && bodyNode == null) {
            bodyNode = nt;
          }
        }
      }

      // Generate init (in current block)
      if (initNode != null && initNode.children().size() > 0) {
        ParseNode initExpr = initNode.children().get(0);
        if (initExpr instanceof NonTerminalNode nt && nt.symbol().equals("<izraz>")) {
          expressionGenerator.emitRValue(nt, functionContext);
        }
      }
      builder.setTerminator(new IrTerminator.IrJmpTerm(condLabel));

      // Condition block
      builder.startBlock(condLabel);
      boolean hasCondition = false;
      if (condNode != null && condNode.children().size() > 0) {
        ParseNode condExpr = condNode.children().get(0);
        if (condExpr instanceof NonTerminalNode nt && nt.symbol().equals("<izraz>")) {
          IrValue condition = expressionGenerator.emitRValue(nt, functionContext);
          Type condType = nt.attributes().type();
          condition = ConditionConverter.convertToBool(condition, condType, builder);
          builder.setTerminator(new IrTerminator.IrBrTerm(condition, bodyLabel, afterLabel));
          hasCondition = true;
        }
      }
      if (!hasCondition) {
        // No condition - always true, jump to body
        builder.setTerminator(new IrTerminator.IrJmpTerm(bodyLabel));
      }

      // Body block
      builder.startBlock(bodyLabel);
      if (bodyNode != null) {
        statementGenerator.generateStatement(bodyNode, functionContext);
      }
      boolean bodyTerminated = currentBlockHasTerminator(builder);
      
      if (!bodyTerminated) {
        // Body doesn't terminate - append step code to body block, then jump back to condition
        if (stepNode != null) {
          expressionGenerator.emitRValue(stepNode, functionContext);
        }
        builder.setTerminator(new IrTerminator.IrJmpTerm(condLabel));
      } else {
        // Body terminated (return/break/continue) - need separate step block for continue
        // Create stepLabel now (after bodyLabel, before afterLabel) for correct ordering
        if (stepLabel == null) {
          stepLabel = builder.labelFactory().newLabel();
          // Update loop context with stepLabel for continue statements
          functionContext.setLoopContext(new LoopContext(afterLabel, stepLabel));
        }
        builder.startBlock(stepLabel);
        if (stepNode != null) {
          expressionGenerator.emitRValue(stepNode, functionContext);
        }
        builder.setTerminator(new IrTerminator.IrJmpTerm(condLabel));
      }

      // After block (exit)
      builder.startBlock(afterLabel);
    } finally {
      functionContext.setLoopContext(previousLoopContext);
    }
  }

  private void generateDoWhileLoop(NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.size() < 6) {
      return;
    }

    IrFunctionBuilder builder = functionContext.functionBuilder();

    // Canonical do-while CFG: entry -> bodyLabel -> condLabel -> (bodyLabel, afterLabel)
    String bodyLabel = builder.labelFactory().newLabel();
    String condLabel = builder.labelFactory().newLabel();
    String afterLabel = builder.labelFactory().newLabel();

    LoopContext previousLoopContext = functionContext.loopContext();
    functionContext.setLoopContext(new LoopContext(afterLabel, condLabel));

    try {
      // Jump from entry to body
      builder.setTerminator(new IrTerminator.IrJmpTerm(bodyLabel));

      // Body block
      builder.startBlock(bodyLabel);
      ParseNode bodyNode = children.get(1);
      if (bodyNode instanceof NonTerminalNode nt && nt.symbol().equals("<naredba>")) {
        statementGenerator.generateStatement(nt, functionContext);
      }
      // Only add jump to condition if body doesn't already have a terminator
      if (!currentBlockHasTerminator(builder)) {
        builder.setTerminator(new IrTerminator.IrJmpTerm(condLabel));
      }

      // Condition block
      builder.startBlock(condLabel);
      ParseNode condNode = children.get(4);
      if (condNode instanceof NonTerminalNode nt && nt.symbol().equals("<izraz>")) {
        IrValue condition = expressionGenerator.emitRValue(nt, functionContext);
        Type condType = nt.attributes().type();
        condition = ConditionConverter.convertToBool(condition, condType, builder);
        builder.setTerminator(new IrTerminator.IrBrTerm(condition, bodyLabel, afterLabel));
      } else {
        // No condition - jump to exit
        builder.setTerminator(new IrTerminator.IrJmpTerm(afterLabel));
      }

      // After block (exit)
      builder.startBlock(afterLabel);
    } finally {
      functionContext.setLoopContext(previousLoopContext);
    }
  }

  /**
   * Checks if the current block has a terminator.
   * Returns true if the current block has been terminated (getCurrentBlockLabel() returns null).
   */
  private boolean currentBlockHasTerminator(IrFunctionBuilder builder) {
    return builder.getCurrentBlockLabel() == null;
  }
}
