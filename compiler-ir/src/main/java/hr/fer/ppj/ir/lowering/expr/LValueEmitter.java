package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.semantics.tree.NonTerminalNode;

/**
 * Interface for emitting l-values (addresses).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public interface LValueEmitter {
  IrTemp emitLValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext);
}
