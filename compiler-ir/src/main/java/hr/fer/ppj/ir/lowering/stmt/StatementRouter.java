package hr.fer.ppj.ir.lowering.stmt;

import hr.fer.ppj.ir.lowering.ExpressionGenerator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.lowering.StatementGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Routes statement generation to appropriate specialized generators.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StatementRouter {

  private final ExpressionStatementGenerator expressionStatementGenerator;
  private final JumpStatementGenerator jumpStatementGenerator;
  private final IfStatementGenerator ifStatementGenerator;
  private final LoopStatementGenerator loopStatementGenerator;
  private final StatementGenerator statementGenerator;

  public StatementRouter(
      ExpressionGenerator expressionGenerator, StatementGenerator statementGenerator) {
    this.expressionStatementGenerator = new ExpressionStatementGenerator(expressionGenerator);
    this.jumpStatementGenerator = new JumpStatementGenerator(expressionGenerator);
    this.statementGenerator =
        Objects.requireNonNull(statementGenerator, "statementGenerator must not be null");
    this.ifStatementGenerator =
        new IfStatementGenerator(expressionGenerator, statementGenerator);
    this.loopStatementGenerator =
        new LoopStatementGenerator(expressionGenerator, statementGenerator);
  }

  /**
   * Generates a statement based on its type.
   */
  public void generateStatement(NonTerminalNode node, FunctionContext functionContext) {
    Objects.requireNonNull(node, "node must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    String nodeSymbol = node.symbol();

    if (nodeSymbol.equals("<naredba_grananja>")) {
      ifStatementGenerator.generateIfStatement(node, functionContext);
    } else if (nodeSymbol.equals("<naredba_petlje>")) {
      loopStatementGenerator.generateLoopStatement(node, functionContext);
    } else if (nodeSymbol.equals("<naredba_skoka>")) {
      jumpStatementGenerator.generateJumpStatement(node, functionContext);
    } else if (nodeSymbol.equals("<izraz_naredba>")) {
      expressionStatementGenerator.generateExpressionStatement(node, functionContext);
    } else if (nodeSymbol.equals("<slozena_naredba>")) {
      statementGenerator.generateCompoundStatement(node, functionContext, false);
    } else {
      // Check first child
      var children = node.children();
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode firstChild) {
        generateStatement(firstChild, functionContext);
      }
    }
  }
}
