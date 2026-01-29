package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.semantics.tree.NonTerminalNode;

/**
 * Interface for emitting expression r-values.
 *
 * <p>Used to break circular dependencies between expression generators.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public interface ExpressionEmitter {
  /**
   * Emits an r-value for an expression node.
   *
   * @param node the expression node
   * @param functionContext the function context
   * @return the r-value
   */
  IrValue emitRValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext);
}
