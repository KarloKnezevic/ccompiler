package hr.fer.ppj.ir.lowering.global;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.util.ConstantEvaluator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Extracts and evaluates global variable initializers.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class GlobalInitializerExtractor {

  /**
   * Extracts a constant initializer from an initializer node.
   *
   * @param initializerNode the initializer node
   * @param varType the variable type
   * @return the constant initializer, or null if not constant
   */
  public IrConst extractInitializer(NonTerminalNode initializerNode, Type varType) {
    Objects.requireNonNull(initializerNode, "initializerNode must not be null");
    Objects.requireNonNull(varType, "varType must not be null");

    // Check if this is an array type with array initializer
    if (varType instanceof ArrayType arrayType) {
      if (initializerNode.children().size() >= 3) {
        var initFirstChild = initializerNode.children().get(0);
        if (initFirstChild instanceof hr.fer.ppj.semantics.tree.TerminalNode term
            && term.symbol().equals("L_VIT_ZAGRADA")) {
          try {
            return ConstantEvaluator.evaluateGlobalArrayInitializer(initializerNode, arrayType);
          } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return null;
          }
        }
      } else if (initializerNode.children().size() == 1) {
        ParseNode firstChild = initializerNode.children().get(0);
        if (firstChild instanceof NonTerminalNode expr) {
          try {
            return ConstantEvaluator.extractConstantFromExpression(expr, arrayType);
          } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return null;
          }
        }
      }
    } else {
      // Scalar global - can have constant initializer
      // The initializer's first child is an expression (could be <izraz_pridruzivanja>, <izraz>, etc.)
      ParseNode firstChild = initializerNode.children().get(0);
      if (firstChild instanceof NonTerminalNode expr) {
        try {
          return ConstantEvaluator.extractConstantFromExpression(expr, varType);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
          return null;
        }
      }
    }

    return null;
  }
}
